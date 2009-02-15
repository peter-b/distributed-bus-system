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

import javax.bluetooth.LocalDevice;
import javax.bluetooth.BluetoothStateException;

import javax.microedition.io.StreamConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.FilterInputStream;
import java.io.OutputStream;
import java.io.FilterOutputStream;

import lejos.nxt.comm.Bluetooth;
import lejos.nxt.comm.NXTConnection;

/** <p>Manages Bluetooth-based bus connections.</p>
 *
 * <p> The single <code>BluetoothConnectionManager</code>, obtained
 * via <code>getConnectionManager()</code>, provides services for
 * creating distributed bus connections over Bluetooth.</p>
 *
 * @see SystemBus
 * @see #getConnectionManager()
 */
public class BluetoothConnectionManager implements BusConnectionServer {

    /** The default RFCOMM channel to use */
    private static final int RFCOMM_CHANNEL = 1;

    /** The interface address of Bluetooth interface */;
    private InterfaceAddress localAddress;

    /* ************************************************** */

    /** Connect to a remote Bluetooth device.
     *
     * @param bdAddress The 48-bit Bluetooth address of the device to
     *                  connect to, as a hexadecimal string.
     *
     * @return a newly-established <code>BusConnection</code>.
     */
    public BusConnection connectDevice(String bdAddress)
        throws IOException {
        byte[] addr = new byte[6];

        for (int i = 0; i < 6; i++) {
            int c = Integer.digit(bdAddress.charAt(2*i), 16) << 4;
            c += Integer.digit(bdAddress.charAt(2*i + 1), 16);
            if (c >= 0x80) c -= 0x100;
            addr[i] = (byte)c;
        }

        lejos.nxt.comm.BTConnection conn =
            Bluetooth.connect(addr);

        if (conn == null)
            throw new IOException ("Could not connect to " +
                                   Bluetooth.addressToString(addr));

        BusConnection result = new BluetoothConnection (conn);
        return result;
    }

    /** BusConnection implementation for Bluetooth links */
    private class BluetoothConnection implements BusConnection {
        private InputStream inStream;
        private OutputStream outStream;
        private StreamConnection conn;
        private boolean closed;

        private InterfaceAddress remoteAddress;

        BluetoothConnection(StreamConnection stream)
            throws IOException {

            conn = stream;
            closed = false;

            inStream = new FilterInputStream(conn.openInputStream()) {
                    public void close() throws IOException {
                        BluetoothConnection.this.disconnect();
                    }
                };

            outStream = new FilterOutputStream(conn.openOutputStream()) {
                    public void close() throws IOException {
                        BluetoothConnection.this.disconnect();
                    }
                };

            /* Exchange interface addresses (should be the first 16
             * bytes sent over connection) */
            outStream.write(getLocalAddress().getBytes());
            byte[] buf = new byte[16];
            inStream.read(buf);
            remoteAddress = new InterfaceAddress(buf);

            SystemBus.getSystemBus().addConnection(this);
        }

        public InputStream getInputStream()
            throws IOException {

            return inStream;
        }

        public OutputStream getOutputStream()
            throws IOException {

            return outStream;
        }

        public synchronized void disconnect() throws IOException {
            if (closed) return;

            /* Unregister with SystemBus */
            SystemBus.getSystemBus().removeConnection(this);

            conn.close();
            closed = true;
        }

        public boolean isConnected() {
            return !closed;
        }

        public InterfaceAddress getLocalAddress() {
            return localAddress;
        }

        public InterfaceAddress getRemoteAddress() {
            return remoteAddress;
        }
    }

    /* ************************************************** */

    private BluetoothServer server;

    /** <p>Set whether connections are accepted from other devices.</p>
     *
     * <p>If <code>enabled</code> is <code>true</code>, accept
     * incoming connections on RFCOMM channel 1.</p>
     *
     * @param enabled If <code>true</code>, listen and accept incoming
     *                Bluetooth connections.
     */
    public void setListenEnabled(boolean enabled) {
        synchronized (server.lock) {
            if (server.enabled == enabled) return;
            if (enabled) {
                server.enabled = false;
            } else {
                server.enabled = true;
                Thread t = new Thread(server);
                t.start();
            }
        }
    }

    /** <p>Test whether connections are accepted from other devices.</p>
     *
     * @return <code>true</code> if listening for incoming connections.
     */
    public boolean isListenEnabled() {
        synchronized (server.lock) {
            return server.enabled;
        }
    }

    private class BluetoothServer implements Runnable {
        boolean enabled;
        Object lock;

        BluetoothServer() {
            enabled = false;
            lock = new Object();
        }

        public void run() {
            synchronized (lock) {
                enabled = false;
            }
            throw new RuntimeException("BT server not implemented");
        }
    }

    /* ************************************************** */

    private static BluetoothConnectionManager instance = null;

    /** Create a new <code>BluetoothConnectionManager</code>. Do not
     * call this directly: use <code>getConnectionManager()</code>.
     *
     * @see #getConnectionManager()
     */
    protected BluetoothConnectionManager() throws BluetoothStateException {
        server = new BluetoothServer();

        String mac = LocalDevice.getLocalDevice().getBluetoothAddress();
        String addr= "0:0:0:0:" + mac.substring(0, 4) + ":" +
            mac.substring(4,6) + "ff:fe" + mac.substring(6,8) + ":" +
            mac.substring(8,12);
        localAddress = new InterfaceAddress(addr);
    }

    /** Get the Bluetooth connection manager
     *
     * @return the global <code>BluetoothConnectionManager</code>.
     */
    public static synchronized BluetoothConnectionManager getConnectionManager() 
    throws BluetoothStateException {
        if (instance == null) {
            instance = new BluetoothConnectionManager();
        }
        return instance;
    }
}
