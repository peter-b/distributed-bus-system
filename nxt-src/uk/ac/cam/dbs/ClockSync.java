/*
 * Distributed bus system for robotic applications
 * Copyright (C) 2009 University of Cambridge
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA
 */

package uk.ac.cam.dbs;

import static uk.ac.cam.dbs.util.ByteBufferHelper.numFromBytes;
import static uk.ac.cam.dbs.util.ByteBufferHelper.numToBytes;
import java.io.IOException;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Enumeration;

/** <p>Clock synchronisation protocol implementation.</p>
 *
 * <p>The clock synchronisation protocol works on a peer-to-peer basis,
 * attempting to synchronise to a network clock that is the average of
 * the internal clocks of all the devices on the network.</p>
 */
public class ClockSync
    extends TimeProvider
    implements DMPMessageListener, Runnable {

    private static final int PORT = 50123;
    private static final int UPDATE_PERIOD = 1000;
    private static final int MAX_STORE_SIZE = 5;

    /* Contains RecvRecord, keyed by BusConnection */
    private Hashtable recvStore;
    /* Contains SendRecord (sent time), keyed by seq % sentStore.size() */
    private long[] sentStore;

    private Object offsetLock;
    private long offset;

    private Object seqLock;
    private int seq; /* Contains *previous* sequence number */

    private double gain;

    private TimeProvider internalTime;

    /** Create a new ClockSync service.
     */
    public ClockSync() {
        this(TimeProvider.systemTimeProvider());
    }

    /** Create a new ClockSync service using a given reference
     * clock. This is mostly intended for testing.
     *
     * @param internalTime TimeProvider instance that the ClockSync
     *        should use as its "reference time".
     */
    public ClockSync(TimeProvider internalTime) {
        this.internalTime = internalTime;
        recvStore = new Hashtable();
        sentStore = new long[10];
        offset = 0;
        offsetLock = new Object();
        seq = 0;
        seqLock = new Object();
        gain = 1.0;
    }

    /** Get the current clock offset estimate.
     *
     * @return the estimated difference between the internal clock
     * source and the network clock.
     */
    public long getOffset(long offset) {
        return offset;
    }

    /** <p>Run the clock synchronisation service. Should be executed in
     * its own thread, e.g.:</p>
     *
     * <code>ClockSync service = new ClockSync();
     * Thread clockSyncThread = new Thread(service);
     * clockSyncThread.setDaemon(true);
     * clockSyncThread.start();</code>
     *
     * <p>The function first binds itself to a DMP port. It then
     * enters a loop which repeatedly sends out clock sync protocol
     * messages, and updates the current clock estimate based on any
     * messages that have been received.</p>
     *
     */
    public void run() {
        try {
            SystemBus.getSystemBus().addDMPService(this, PORT);
        } catch (DMPBindException e) {
            System.err.println("Could not start clock sync service: " +
                               e.getMessage());
            return;
        }

        while (true) {
            /* Send clock sync messages */
            Vector connections = SystemBus.getSystemBus().getConnections();
            synchronized (connections) {
                for (int i = 0; i < connections.size(); i++) {
                    try {
                        sendMessage((BusConnection) connections.elementAt(i));
                    } catch (IOException e) {
                        System.err.println("Failed to send clock message: " + e.getMessage());
                        continue;
                    }
                }
            }

            /* Sleep for UPDATE_PERIOD + a random delay between 0 and
             * UPDATE_PERIOD/2. This is to avoid multiple devices
             * phase-locking their clock sync loops. */
            try {
                int sleepTime = (int) (UPDATE_PERIOD *
                                       (1.0 + Math.random() / 2));
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                System.err.println("Stopping clock sync service.");
                break;
            }

            /* Update estimate */
            updateOffset();
        }
        SystemBus.getSystemBus().removeDMPService(this, -1);
    }

    /** Get the current estimate of network time.
     *
     * @return the current network time estimate in milliseconds.
     */
    public long currentTimeMillis() {
        long sysTime = internalTime.currentTimeMillis();
        return sysTime + offset;
    }

    /** Process a clock protocol message. Calculates the roundtrip
     * latency, and stores message information for the next update to
     * the clock offset estimate.
     *
     * @param connection The connection that the message arrived on.
     * @param msg        The message that was received.
     */
    public void recvDMPMessage(BusConnection connection,
                               DMPMessage msg) {

        long localTime = System.currentTimeMillis();

        /* Parse the message payload */
        byte[] buf = msg.getPayload();
        if (buf.length != 24) {
            /* The message appears to be invalid. */
            return;
        }
        int recvSeq =     (int) numFromBytes(buf,  0, 4);
        long remoteTime =       numFromBytes(buf,  4, 8);
        int oldSeq =      (int) numFromBytes(buf, 12, 4);
        long holdTime =         numFromBytes(buf, 16, 8);

        /* Calculate the latency if we can */
        long sentTime = -1;
        long roundTrip = 0;
        boolean roundTripValid = false;
        synchronized (sentStore) {
            if ((oldSeq == 0) || (oldSeq <= this.seq - 10)) {
                sentTime = -1;
            } else {
                sentTime = sentStore[oldSeq % sentStore.length];
            }
        }
        if (sentTime >= 0) {
            roundTrip = localTime - sentTime - holdTime;
            roundTripValid = true;
        }

        /* Do all the synchronized operations last, in one go, to
         * avoid problems with added latency while waiting to
         * synchronise */
        RecvRecord rec = null;
        synchronized (recvStore) {
            rec = (RecvRecord) recvStore.get(connection);

            if (rec == null) {
                rec = this.new RecvRecord();
                recvStore.put(connection, rec);
            }
            synchronized (rec) {
                rec.seq = recvSeq;
                rec.remoteTime = remoteTime;
                rec.localTime = localTime;
                rec.roundTrip = roundTrip;
                rec.roundTripValid = roundTripValid;
                rec.usedForUpdate = false;
            }
        }
    }

    /** Entry in the record of received packets. */
    private class RecvRecord {
        int seq;
        long localTime;
        long remoteTime;
        long roundTrip;
        boolean roundTripValid;
        boolean usedForUpdate;

        RecvRecord() {
            roundTripValid = false;
            usedForUpdate = false;
        }
    }

    /** Send a clock sync message */
    private void sendMessage (BusConnection connection)
        throws IOException {
        RecvRecord rec = null;
        synchronized (recvStore) {
            rec = (RecvRecord) recvStore.get(connection);
        }
        long now = internalTime.currentTimeMillis();
        byte[] payload = new byte[24];

        /* Get the next sequence number */
        /* Ensure we never send out a message with seq=0 */
        int seq = 0;
        synchronized (seqLock) {
            while (seq == 0) {
                seq = ++this.seq;
            }
        }

        /* Did we receive a message from this connection? */
        int oldSeq = 0;
        long holdTime = 0;
        if (rec != null) {
            oldSeq = rec.seq;
            holdTime = now - rec.localTime;
        }

        /* Pack data into the message payload */
        numToBytes(seq,        payload,  0, 4);
        numToBytes(now+offset, payload,  4, 8);
        numToBytes(oldSeq,     payload, 12, 4);
        numToBytes(holdTime,   payload, 16, 8);

        /* Send message */
        DMPMessage msg = new DMPMessage(PORT, payload);

        /* Add to record of sent messages */
        synchronized(sentStore) {
            sentStore[seq % sentStore.length] = now;
        }

        SystemBus.getSystemBus().sendDMPMessage(connection, msg);
    }

    /** Uses the received messages to calculate a new clock offset. */
    private void updateOffset () {
        synchronized (recvStore) {
            Enumeration conns = recvStore.keys();
            int N = SystemBus.getSystemBus().getConnections().size();
            double e = 0.0;

            synchronized (offsetLock) {
                while (conns.hasMoreElements()) {
                    Object k = conns.nextElement();
                    RecvRecord rec = (RecvRecord) recvStore.get(k);

                    synchronized (rec) {
                        if (rec.roundTripValid && !rec.usedForUpdate) {
                            e += rec.remoteTime + rec.roundTrip/2
                                - rec.localTime - offset;
                            rec.usedForUpdate = true;
                        }
                    }
                }

                offset += (long) gain * e / (N+1.0);
            }
        }
    }
}
