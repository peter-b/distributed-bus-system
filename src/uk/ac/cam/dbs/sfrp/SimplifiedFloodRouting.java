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

package uk.ac.cam.dbs.sfrp;

import uk.ac.cam.dbs.*;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import static uk.ac.cam.dbs.util.ByteBufferHelper.numFromBytesUnsigned;
import static uk.ac.cam.dbs.util.ByteBufferHelper.numToBytes;

/** <p>Simplified Flood Routing Protocol main class.</p>
 *
 * </p>See the package documentation for examples of use.</p>
 *
 * @see uk.ac.cam.dbs.sfrp
 */
public class SimplifiedFloodRouting
    implements Runnable, NamingProvider, RoutingProvider,
               DMPMessageListener {

    int lastSeq;
    Object seqLock;

    /* K = InterfaceAddress, V = DeviceRecord */
    Hashtable devices;
    Vector routeListeners;

    static final int DMP_PORT = 50054;
    static final int HELLO_TIME = 1000;

    /** Initialise a new SFRP daemon. */
    public SimplifiedFloodRouting() {
        lastSeq = 0;
        seqLock = new Object();
        devices = new Hashtable();
        routeListeners = new Vector();
    }

    /** {@inheritDoc}
     * @param dest {@inheritDoc}
     * @return {@inheritDoc}
     */
    public BusConnection nextHop(InterfaceAddress dest) {
        DeviceRecord rec = getDeviceRecord(dest);
        if ((rec == null) || !rec.routeValid) return null;
        return rec.hop;
    }

    /** {@inheritDoc}
     * @param deviceName {@inheritDoc}
     * @return {@inheritDoc}
     */
    public InterfaceAddress getAddressByName(String deviceName) {
        throw new RuntimeException("Not implemented");
    }

    /** {@inheritDoc}
     * @param address {@inheritDoc}
     * @return {@inheritDoc}
     */
    public String getNameByAddress(InterfaceAddress address) {
        throw new RuntimeException("Not implemented");
    }

    /** <p>The main loop method for the SFRP daemon. This should
     * normally not be run directly, but rather via the
     * <code>start()</code> method of a new thread.</p>
     *
     * <p>See the package documentation for example code for starting
     * a SFRP service.</p>
     */
    public void run() {
        try {
            SystemBus.getSystemBus().addDMPService(this, DMP_PORT);
        } catch (DMPBindException e) {
            System.err.println("Could not bind DMP port: " + e.getMessage());
            return;
        }

        while (true) {
            /* First, transmit messages */
            sendHelloMessages();

            /* Purge probably-disconnected devices */
            purgeDeviceRecords();

            try {
                Thread.sleep(HELLO_TIME);
            } catch (InterruptedException e) { }
        }
    }

    /** Add a listener for route change events.
     * @param l  Listener object to add.
     */
    public void addRouteChangeListener(SfrpRouteChangeListener l) {
        synchronized (routeListeners) {
            if (routeListeners.indexOf(l) == -1) {
                routeListeners.addElement(l);
            }
        }
    }

    /** Remove a route change event listener.
     * @param l  Listener object to remove.
     */
    public void removeRouteChangeListener(SfrpRouteChangeListener l) {
        synchronized (routeListeners) {
            routeListeners.removeElement(l);
        }
    }

    /* Notifies all of the listeners that a route has changed */
    private void dispatchRouteChange(InterfaceAddress addr, int status) {
        synchronized (routeListeners) {
            for (int i = 0; i < routeListeners.size(); i++) {
                SfrpRouteChangeListener l =
                    (SfrpRouteChangeListener) routeListeners.elementAt(i);
                l.routeChanged(addr, status);
            }
        }
    }

    /* Sends HELLO message to all adjacent nodes */
    private void sendHelloMessages() {
        InterfaceAddress mainAddress =
            SystemBus.getSystemBus().getMainAddress();
        if (mainAddress == null) return;

        byte[] payload = new byte[24];
        /* Sequence number */
        int seq;
        synchronized (seqLock) {
            seq = ++lastSeq;
        }
        numToBytes(seq % (1<<16), payload, 0, 2);
        /* Hops so far = 1*/
        numToBytes(1, payload, 2, 2);
        /* Time for which to treat HELLO as valid */
        numToBytes(2*HELLO_TIME, payload, 4, 2);
        /* Skip 2 reserved bytes */
        byte[] addrBytes = mainAddress.getBytes();
        for (int i = 0; i < 16; i++) {
            payload[i+8] = addrBytes[i];
        }

        /* Create a DMP message */
        DMPMessage msg = new DMPMessage(DMP_PORT, payload);

        /* Send over each connection */
        SystemBus bus = SystemBus.getSystemBus();
        Vector conns = bus.getConnections();
        for (int i = 0; i < conns.size(); i++) {
            BusConnection c = (BusConnection) conns.elementAt(i);
            try {
                bus.sendDMPMessage(c, msg);
            } catch (IOException e) {
                System.err.println("SFRP flood failed: " + e.getMessage());
            }
        }
    }

    /* Checks through device records, flagging those that have timed out */
    private void purgeDeviceRecords() {
        long now = System.currentTimeMillis();
        synchronized (devices) {
            Enumeration addresses = devices.keys();
            while (addresses.hasMoreElements()) {
                InterfaceAddress addr = (InterfaceAddress) addresses.nextElement();
                DeviceRecord rec = getDeviceRecord(addr);
                if (!rec.routeValid) continue;

                if (now - rec.lastUpdate > rec.validTime) {
                    rec.routeValid = false;
                    /* Notify listeners that the route has gone */
                    dispatchRouteChange(addr, SfrpRouteChangeListener.ROUTE_REMOVED);
                }
            }
        }
    }

    private DeviceRecord getDeviceRecord(InterfaceAddress mainAddress) {
        synchronized (devices) {
            return (DeviceRecord) devices.get(mainAddress);
        }
    }

    private void addDeviceRecord(DeviceRecord record) {
        synchronized (devices) {
            devices.put(record.mainAddress, record);
        }
    }

    private class DeviceRecord {
        int seq;
        int dist;
        int validTime;
        InterfaceAddress mainAddress;
        BusConnection hop;
        long lastUpdate;
        boolean routeValid;

        DeviceRecord(InterfaceAddress addr) {
            seq = -1;
            dist = (1 << 31) ^ -1; /* Max int */
            validTime = 0;
            mainAddress = addr;
            hop = null;
            lastUpdate = 0;
            routeValid = false;
        }
    }

    /** Handle a received DMP message. Processes the received message,
     * updating the routing database and forwarding the message to
     * other network nodes as required.
     *
     * @param conn {@inheritDoc}
     * @param msg {@inheritDoc}
     */
    public void recvDMPMessage(BusConnection conn, DMPMessage msg) {
        /* For now, devices don't advertise name or alternative
         * interface addresses, so the payload should be a fixed
         * size:
         *
         * 16  bits: message sequence number
         * 16  bits: number of hops so far
         * 16  bits: validity time (milliseconds)
         * 16  bits: reserved
         * 128 bits: main address
         *
         * Total length: 24 bytes.
         */
        byte[] payload = msg.getPayload();
        if (payload.length != 24) {
            System.out.println("SFRP message malformed: bad DMP payload length.");
            return;
        }

        /* Read message sequence number */
        int seq = (int) numFromBytesUnsigned(payload, 0, 2);
        /* Read number of hops */
        int hops = (int) numFromBytesUnsigned(payload, 2, 2);
        /* Read validity period */
        int validTime = (int) numFromBytesUnsigned(payload, 4, 2);
        /* Skip 2 reserved bytes */
        /* Read main address */
        byte[] addrBytes = new byte[16];
        for (int i = 0; i < 16; i++)
            addrBytes[i] = payload[8+i];
        InterfaceAddress deviceAddr = new InterfaceAddress(addrBytes);

        /* If this is our own message, ignore it completely. */
        if (deviceAddr.equals(SystemBus.getSystemBus().getMainAddress())) {
            return;
        }

        DeviceRecord record = getDeviceRecord(deviceAddr);
        boolean relay = false;
        boolean newRoute = false;
        boolean changedRoute = false;
        if (record == null) {
            record = new DeviceRecord(deviceAddr);
            addDeviceRecord(record);
            relay = true;
            newRoute = true;
        }

        if (!relay) {
            /* If message that arrived has newer sequence number,
             * update & relay. If seq is a *lot* less than the last
             * sequence number seen, assume that it has wrapped
             * around. */
            if (seq > record.seq) {
                relay = true;
            } else if (seq < record.seq - 32768) {
                relay = true;
            }
            /* If message that arrived came by shorter route, update & relay */
            if ((seq == record.seq) && (hops < record.dist)) {
                relay = true;
            }
        }

        /* If we've decided this isn't worth relaying, bail out now */
        if (!relay) return;
        /* If the route had been purged by the invalid timer, mark it
         * as new */
        if (!record.routeValid) newRoute = true;

        /* Update record */
        record.lastUpdate = System.currentTimeMillis();
        record.seq = seq;
        record.dist = hops;
        record.validTime = validTime;
        record.hop = conn;
        record.routeValid = true;

        /* Increment hop count */
        numToBytes(hops + 1, payload, 2, 2);
        DMPMessage relaymsg = new DMPMessage(DMP_PORT, payload);
        SystemBus bus = SystemBus.getSystemBus();

        /* Relay message to all neighbours apart from sender */
        Vector connections = bus.getConnections();
        for (int i = 0; i < connections.size(); i++) {
            BusConnection relayconn = (BusConnection) connections.elementAt(i);
            if (relayconn == conn) continue; /* Skip sender */
            try {
                bus.sendDMPMessage(relayconn, relaymsg);
            } catch (IOException e) {
                System.err.println("SFRP relay failed: " + e.getMessage());
            }
        }

        /* Notify listeners if this is a new route */
        if (newRoute)
            dispatchRouteChange(deviceAddr, SfrpRouteChangeListener.ROUTE_ADDED);
    }
}
