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
 * <p>FIXME Do something more sensible WRT shutting down server
 * thread.</p>
 *
 * @see SystemBus
 * @see #getConnectionManager()
 */
public class BluetoothConnectionManager implements BusConnectionServer {

    /** The default RFCOMM channel to use */
    private static final int RFCOMM_CHANNEL = 1;

    /** The name used to describe this service. */
    private static final String SERVICE_NAME = "DistributedBus";

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

        public byte[] getLocalAddress() {
            throw new UnsupportedOperationException();
        }
    }

    /* ************************************************** */

    private Thread serverThread;

    /** <p>Set whether connections are accepted from other devices.</p>
     *
     * <p>If <code>enabled</code> is <code>true</code>, accept
     * incoming connections on RFCOMM channel 1.</p>
     *
     * @param enabled If <code>true</code>, listen and accept incoming
     *                Bluetooth connections.
     */
    public synchronized void setListenEnabled(boolean enabled) {

        if (!enabled) {
            if (!isListenEnabled()) return;

            serverThread.interrupt();
            try {
                serverThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            serverThread = null;
        } else {
            if (isListenEnabled()) return;

            serverThread = new Thread (new BluetoothServer());

            /* Don't let server thread keep the program running. */
            serverThread.setDaemon(true);
            serverThread.start();
        }
        /* FIXME should we check the server actually has started
         * successfully, and throw an Exception if it doesn't? */
    }

    /** <p>Test whether connections are accepted from other devices.</p>
     *
     * @return <code>true</code> if listening for incoming connections.
     */
    public boolean isListenEnabled() {
        if (serverThread != null) {
            return serverThread.isAlive();
        } else {
            return false;
        }
    }

    private class BluetoothServer implements Runnable {

        public void run() {
            StreamConnectionNotifier service = null;

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
                service = (StreamConnectionNotifier)
                    Connector.open("btspp://localhost:" + Integer.toString(RFCOMM_CHANNEL)
                                   + ";authenticate=false;encrypt=false;name=" + SERVICE_NAME);


                while (true) {
                    /* accept/open */
                    StreamConnection conn = service.acceptAndOpen();
                    /* If interrupted, close everything & stop */
                    if (Thread.interrupted()) {
                        if (conn != null) conn.close();
                        break;
                    }
                    /* Create & register bus connection */
                    BluetoothConnectionManager.this.new
                        BluetoothConnection(conn);
                }

            } catch (IOException e) {
                System.err.printf("SPP server error: %1$s", e.getMessage());
            } finally {
                if (service != null) {
                    try {
                        service.close();
                    } catch (IOException e) { }
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
    protected BluetoothConnectionManager() {
        serverThread = null;
    }

    /** Get the Bluetooth connection manager
     *
     * @return the global <code>BluetoothConnectionManager</code>.
     */
    public static synchronized BluetoothConnectionManager getConnectionManager() {
        if (instance == null) {
            instance = new BluetoothConnectionManager();
        }
        return instance;
    }
}
