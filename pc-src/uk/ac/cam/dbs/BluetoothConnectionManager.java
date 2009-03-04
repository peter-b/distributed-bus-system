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

import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.FilterInputStream;
import java.io.OutputStream;
import java.io.FilterOutputStream;

/** <p>Manages Bluetooth-based bus connections.</p>
 *
 * <p> The single <code>BluetoothConnectionManager</code>, obtained
 * via <code>getConnectionManager()</code>, provides services for
 * creating distributed bus connections over Bluetooth.</p>
 *
 * <p>It is designed to be compatible with the LEGO Mindstorms NXT.</p>
 *
 * @see SystemBus
 * @see #getConnectionManager()
 */
public class BluetoothConnectionManager implements BusConnectionServer {

    /** The default RFCOMM channel to use */
    private static final int RFCOMM_CHANNEL = 1;

    /** The name used to describe this service. */
    private static final String SERVICE_NAME = "DistributedBus";

    /** The interface address of Bluetooth interface */;
    private InterfaceAddress localAddress;

    /** Get the local interface address.
     *
     * @return the interface address used by Bluetooth
     * <code>BusConnection</code>s created by this connection manager.
     *
     * @see BusConnection#getLocalAddress()
     */
    public InterfaceAddress getLocalAddress() {
        if (localAddress == null) {
        }
        return localAddress;
    }

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

        String handle = "btspp://" + bdAddress + ":" +
            Integer.toString(RFCOMM_CHANNEL);

        StreamConnection conn = (StreamConnection) Connector.open(handle);

        return new BluetoothConnection (conn);
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
            return BluetoothConnectionManager.this.getLocalAddress();
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
            if (!enabled) {
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
        StreamConnectionNotifier notifier;

        BluetoothServer() {
            enabled = false;
            lock = new Object();
            notifier = null;
        }

        public void run() {

            try {
                /* Open RFCOMM server. This does extremely badly
                 * documented magic.  Essentially: using "btspp" as the
                 * protocol part specifies an RFCOMM connection, and the
                 * fact that "localhost" is the host part specifies a
                 * server (no loopback connections!) These two factors
                 * determine that Connector.open() will return a
                 * StreamConnectionNotifier.
                 *
                 * The "name" parameter is used when registering with the
                 * SDP service (so other devices know which RFCOMM channel
                 * to use to connect to this SPP server), but by
                 * specifying the channel directly rather than providing a
                 * UUID, we won't get registered with the SDP at all.
                 */
                notifier = (StreamConnectionNotifier)
                    Connector.open("btspp://localhost:" + Integer.toString(RFCOMM_CHANNEL)
                                   + ";authenticate=false;encrypt=false;name=" + SERVICE_NAME);


                while (true) {
                    /* accept/open */
                    StreamConnection conn = notifier.acceptAndOpen();
                    /* If listening is no longer enabled, drop the
                     * connection immediately. */
                    synchronized (lock) {
                        if (!enabled) {
                            conn.close();
                            break;
                        }
                    }
                    /* Create & register bus connection */
                    BluetoothConnectionManager.this.new
                        BluetoothConnection(conn);
                }

            } catch (IOException e) {
                /* Only display an error message if listenning is
                 * enabled */
                synchronized (lock) {
                    if (enabled) {
                        System.err.println("SPP server error: " +
                                           e.getMessage());
                    }
                }
            } finally {
                /* Make sure we *always* shut down the notifier */
                if (notifier != null) {
                    try {
                        notifier.close();
                    } catch (IOException e) {
                    } finally {
                        notifier = null;
                    }
                }
                synchronized (lock) {
                    enabled = false;
                }
            }
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

        String macStr = LocalDevice.getLocalDevice().getBluetoothAddress();
        byte[] mac = new byte[6];
        for (int i = 0; i < 6; i++) {
            String hex = macStr.substring(i*2, i*2+2);
            int x = Integer.parseInt(hex,16);
            if (x >= 0x80) x -= 0x100;
            mac[i] = (byte) x;
        }
        localAddress = new Rfc4193InterfaceAddress(mac);
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
