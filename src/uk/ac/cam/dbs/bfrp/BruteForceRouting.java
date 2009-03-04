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

package uk.ac.cam.dbs.bfrp;

import uk.ac.cam.dbs.*;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import static uk.ac.cam.dbs.util.ByteBufferHelper.numFromBytesUnsigned;
import static uk.ac.cam.dbs.util.ByteBufferHelper.numToBytes;

public class BruteForceRouting
    implements Runnable, NamingProvider, RoutingProvider,
               UDPMessageListener {

    int lastSeq;
    Object seqLock;

    InterfaceAddress mainAddr;

    /* K = InterfaceAddress, V = DeviceRecord */
    Hashtable devices;
    Vector routeListeners;

    static final int UDP_PORT = 50054;
    static final int HELLO_TIME = 1000;

    public BruteForceRouting() {
        lastSeq = 0;
        seqLock = new Object();
        mainAddr = null;
        devices = new Hashtable();
        routeListeners = new Vector();
    }

    public BruteForceRouting(InterfaceAddress mainAddress) {
        this();
        this.setMainAddress(mainAddress);
    }

    public BusConnection nextHop(InterfaceAddress dest) {
        DeviceRecord rec = getDeviceRecord(dest);
        if ((rec == null) || !rec.routeValid) return null;
        return rec.hop;
    }

    public InterfaceAddress getAddressByName(String deviceName) {
        throw new RuntimeException("Not implemented");
    }

    public String getNameByAddress(InterfaceAddress address) {
        throw new RuntimeException("Not implemented");
    }

    public void run() {
        try {
            SystemBus.getSystemBus().addUDPService(this, UDP_PORT);
        } catch (UDPBindException e) {
            System.err.println("Could not bind UDP port: " + e.getMessage());
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

    public void addRouteChangeListener(BfrpRouteChangeListener l) {
        synchronized (routeListeners) {
            if (routeListeners.indexOf(l) == -1) {
                routeListeners.addElement(l);
            }
        }
    }

    public void removeRouteChangeListener(BfrpRouteChangeListener l) {
        synchronized (routeListeners) {
            routeListeners.removeElement(l);
        }
    }

    private void dispatchRouteChange(InterfaceAddress addr, int status) {
        synchronized (routeListeners) {
            for (int i = 0; i < routeListeners.size(); i++) {
                BfrpRouteChangeListener l =
                    (BfrpRouteChangeListener) routeListeners.elementAt(i);
                l.routeChanged(addr, status);
            }
        }
    }

    public void setMainAddress(InterfaceAddress addr) {
        mainAddr = addr;
    }

    public InterfaceAddress getMainAddress() {
        return mainAddr;
    }

    private void sendHelloMessages() {
        byte[] payload = new byte[24];
        /* Message size */
        numToBytes(24, payload, 0, 2);
        /* Sequence number */
        int seq;
        synchronized (seqLock) {
            seq = ++lastSeq;
        }
        numToBytes(seq % (1<<16), payload, 2, 2);
        /* Hops so far = 1*/
        numToBytes(1, payload, 4, 2);
        /* Reserved */
        /* FIXME should be validity time for message */
        numToBytes(0, payload, 6, 2);
        byte[] addrBytes = getMainAddress().getBytes();
        for (int i = 0; i < 16; i++) {
            payload[i+8] = addrBytes[i];
        }

        /* Create a UDP message */
        UDPMessage msg = new UDPMessage(UDP_PORT, UDP_PORT, payload);

        /* Send over each connection */
        SystemBus bus = SystemBus.getSystemBus();
        Vector conns = bus.getConnections();
        for (int i = 0; i < conns.size(); i++) {
            BusConnection c = (BusConnection) conns.elementAt(i);
            try {
                bus.sendUDPMessage(c, msg);
            } catch (IOException e) {
                System.err.println("BFRP flood failed: " + e.getMessage());
            }
        }
    }

    private void purgeDeviceRecords() {
        long now = System.currentTimeMillis();
        synchronized (devices) {
            Enumeration addresses = devices.keys();
            while (addresses.hasMoreElements()) {
                InterfaceAddress addr = (InterfaceAddress) addresses.nextElement();
                DeviceRecord rec = getDeviceRecord(addr);
                if (!rec.routeValid) continue;

                /* FIXME should use timeout provided by remote device */
                if (now - rec.lastUpdate > 2 * HELLO_TIME) {
                    rec.routeValid = false;
                    /* Notify listeners that the route has gone */
                    dispatchRouteChange(addr, BfrpRouteChangeListener.ROUTE_REMOVED);
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
        InterfaceAddress mainAddress;
        BusConnection hop;
        long lastUpdate;
        boolean routeValid;

        DeviceRecord(InterfaceAddress addr) {
            seq = -1;
            dist = (1 << 31) ^ -1; /* Max int */
            mainAddress = addr;
            hop = null;
            lastUpdate = 0;
            routeValid = false;
        }
    }

    public void recvUDPMessage(BusConnection conn, UDPMessage msg) {
        /* For now, devices don't advertise name or alternative
         * interface addresses, so the payload should be a fixed
         * size:
         *
         * 16  bits: message length in octets
         * 16  bits: message sequence number
         * 16  bits: number of hops so far
         * 16  bits: reserved (FIXME should be validity time for message)
         * 128 bits: main address
         *
         * Total length: 24 bytes.
         */
        byte[] payload = msg.getPayload();
        if (payload.length != 24) {
            System.out.println("BFRP message malformed: bad UDP payload length.");
            return;
        }

        /* Read message length */
        int size = (int) numFromBytesUnsigned(payload, 0, 2);
        /* Read message sequence number */
        int seq = (int) numFromBytesUnsigned(payload, 2, 2);
        /* Read number of hops */
        int hops = (int) numFromBytesUnsigned(payload, 4, 2);
        /* Read main address */
        byte[] addrBytes = new byte[16];
        for (int i = 0; i < 16; i++)
            addrBytes[i] = payload[8+i];
        InterfaceAddress deviceAddr = new InterfaceAddress(addrBytes);

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
            /* If message that arrived has newer sequence number, update & relay */
            if (seq > record.seq) {
                relay = true;
            }
            /* If message that arrived came by shorter route, update & relay */
            if (hops < record.dist) {
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
        record.hop = conn;
        record.routeValid = true;

        /* Increment hop count */
        numToBytes(hops + 1, payload, 4, 2);
        UDPMessage relaymsg = new UDPMessage(UDP_PORT, UDP_PORT, payload);
        SystemBus bus = SystemBus.getSystemBus();

        /* Relay message to all neighbours apart from sender */
        Vector connections = bus.getConnections();
        for (int i = 0; i < connections.size(); i++) {
            BusConnection relayconn = (BusConnection) connections.elementAt(i);
            if (relayconn == conn) continue; /* Skip sender */
            try {
                bus.sendUDPMessage(relayconn, relaymsg);
            } catch (IOException e) {
                System.err.println("BFRP relay failed: " + e.getMessage());
            }
        }

        /* Notify listeners if this is a new route */
        if (newRoute)
            dispatchRouteChange(deviceAddr, BfrpRouteChangeListener.ROUTE_ADDED);
    }
}
