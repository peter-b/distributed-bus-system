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
    implements UDPMessageListener, Runnable {

    private static final int PORT = 50123;
    private static final int UPDATE_PERIOD = 1000;

    /* Contains RecvRecord, keyed by BusConnection */
    private Hashtable recvStore;
    /* Contains Long (sent time), keyed by Integer (seq) */
    private Hashtable sentStore;

    private Object offsetLock;
    private long offset;

    private Object seqLock;
    private int seq;

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
        sentStore = new Hashtable();
        offset = 0;
        offsetLock = new Object();
        seq = 1;
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
     * <p>The function first binds itself to a UDP port. It then
     * enters a loop which repeatedly sends out clock sync protocol
     * messages, and updates the current clock estimate based on any
     * messages that have been received.</p>
     *
     */
    public void run() {
        try {
            SystemBus.getSystemBus().addUDPService(this, PORT);
        } catch (UDPBindException e) {
            System.err.println("Could not start clock sync service: " +
                               e.getMessage());
            return;
        }

        while (true) {
            /* Send clock sync messages */
            Vector connections = SystemBus.getSystemBus().getConnections();
            Iterator i = connections.iterator();
            while (i.hasNext()) {
                try {
                    sendMessage((BusConnection) i.next());
                } catch (IOException e) {
                    System.err.println("Failed to send clock message: " + e.getMessage());
                    continue;
                }
            }

            try {
                Thread.sleep(UPDATE_PERIOD);
            } catch (InterruptedException e) {
                System.err.println("Stopping clock sync service.");
                break;
            }

            /* Update estimate */
            updateOffset();
        }
        SystemBus.getSystemBus().removeUDPService(this, -1);
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
    public void recvUDPMessage(BusConnection connection,
                               UDPMessage msg) {

        RecvRecord rec = this.new RecvRecord();

        /* Parse the message payload */
        byte[] buf = msg.getPayload();
        if (buf.length != 24) {
            /* The message appears to be invalid. */
            return;
        }
        rec.seq =        (int) numFromBytes(buf,  0, 4);
        rec.remoteTime =       numFromBytes(buf,  4, 8);
        int oldSeq =     (int) numFromBytes(buf, 12, 4);
        long holdTime =        numFromBytes(buf, 16, 8);

        /* Calculate the latency if we can */
        Long sendTime = null;
        synchronized (sentStore) {
            sendTime = (Long) sentStore.get(new Integer(oldSeq));
        }
        if (sendTime != null) {
            rec.roundTrip = rec.localTime - sendTime.longValue() - holdTime;
            rec.roundTripValid = true;
        }

        /* Record that we received a message */
        synchronized (recvStore) {
            recvStore.put(connection, rec);
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
            localTime = ClockSync.this.internalTime.currentTimeMillis();
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
        int seq;
        synchronized (seqLock) {
            seq = this.seq++;
            if (this.seq == 0) this.seq++;
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
        UDPMessage msg = new UDPMessage(PORT, PORT,
                                        payload);
        SystemBus.getSystemBus().sendUDPMessage(connection, msg);

        /* Add to record of sent messages */
        sentStore.put(new Integer(seq), new Long(now));
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

                    if (rec.roundTripValid && !rec.usedForUpdate) {
                        e += rec.remoteTime + rec.roundTrip/2
                            - rec.localTime - offset;
                        rec.usedForUpdate = true;
                    }
                }

                offset += (long) gain * e / (N+1.0);
            }
        }
    }

    /** <p>Parses a signed integer from a byte array. Parses an
     * integer of <code>len*8</code> bytes from the byte array
     * <code>b</code>, with the MSB at <code>b[off]<code> and the LSB
     * at <code>b[off+len-1<code>. Assumes that the number is two's
     * complement encoded, and in network byte order.</p>
     *
     * @param b   Byte array to parse.
     * @param off Offset within <code>b</code> of MSB of integer.
     * @param len Length of integer in octets.
     *
     * @return The number retrieved.
     */
    private long numFromBytes(byte[] b, int off, int len) {
        long result;

        /* First, pad the result with 1s or 0s depending on the MSB of
         * the stored number */
        if ((b[off] & 0x80) != 0) {
            result = ~(0L);
        } else {
            result = 0L;
        }

        /* Then shift in the data */
        for (int i = 0; i < len; i++) {
            result = (result << 8) | (0xff & b[off+i]);
        }

        return result;
    }


    /** <p>Writes a signed integer into a byte array. Writes an
     * integer of <code>len*8</code> bytes into the byte array
     * <code>b</code>, with the MSB at <code>b[off]<code> and the LSB
     * at <code>b[off+len-1<code>. The data is encoded in two's
     * complement format, and in network byte order.</p>
     *
     * @param b   Byte array to write to.
     * @param off Offset within <code>b</code> of MSB of integer.
     * @param len Length of integer in octets.
     */
    private void numToBytes(long value, byte[] b, int off, int len) {
        /* Shift in the data */
        long shift = value;
        for (int i = len - 1; i >= 0; i--) {
            long v = shift & 0xff;
            if ((v & 0x80) > 0) {
                v -= 0x100;
            }
            b[off+i] = (byte) v;
            shift = shift >> 8;
        }
    }
}
