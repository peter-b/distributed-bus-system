package uk.ac.cam.dbs;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import java.io.IOException;
import java.io.InputStream;
import java.io.FilterInputStream;
import java.io.OutputStream;
import java.io.FilterOutputStream;

/** <p>Manages bus connections tunnelled over TCP/IP.</p>
 *
 * <p>The single <code>TCPIPConnectionManager</code> instance, obtained
 * via <code>getConnectionManager()</code>, provides a way of
 * tunnelling the distributed bus over TCP/IP links (e.g. the
 * Internet).</p>
 *
 * <p>It provides ways of initiating connections to remote devices as
 * well as a service for listening for incoming connections (the
 * default TCP port is 51992).</p>
 *
 * <p>FIXME Do something more sensible WRT shutting down server
 * thread.</p>
 *
 * @see SystemBus
 * @see #getConnectionManager()
 */
public class TCPIPConnectionManager implements BusConnectionServer {
    private int tcp_port = 51992;

    /* ************************************************** */

    /** <p>Connect to a remote host over TCP/IP. Equivalent to
     * calling:</p>
     *
     * <p><code>connectHost(hostname, getTCPPort())</code></p>
     *
     * @param hostname The hostname of the remote device.
     *
     * @return a newly-established <code>BusConnection</code>.
     *
     * @see #connectHost(String, int)
     */
    public BusConnection connectHost(String hostname)
        throws IOException {

        return connectHost(hostname, tcp_port);
    }

    /** <p>Connect to a remote host over TCP/IP. The remote device,
     * identified by <code>hostname</code>, must be listening for
     * incoming connections on the given <code>port</code>.
     *
     * <p>The resulting <code>BusConnection</code> is automatically
     * registered with the <code>SystemBus</code>.</p>
     *
     * @param hostname The hostname of the remote device.
     * @param port     The TCP port to connect to.
     *
     * @return a newly-established <code>BusConnection</code>.
     */
    public BusConnection connectHost(String hostname, int port)
        throws IOException {

        InetAddress hostAddr = InetAddress.getByName(hostname);
        Socket sock = new Socket(hostAddr, port);

        return this.new TCPIPConnection(sock);
    }

    /** BusConnection implementation for TCP/IP tunnels. */
    private class TCPIPConnection implements BusConnection {
        private Socket socket;
        private InputStream inStream;
        private OutputStream outStream;

        TCPIPConnection(Socket sock) throws IOException {
            socket = sock;

            inStream = new FilterInputStream(socket.getInputStream()) {
                public void close() throws IOException {
                    TCPIPConnection.this.disconnect();
                }
            };
            outStream = new FilterOutputStream(socket.getOutputStream()) {
                public void close() throws IOException {
                    TCPIPConnection.this.disconnect();
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
            if (socket.isClosed()) {
                return;
            }

            /* Unregister with SystemBus */
            SystemBus.getSystemBus().removeConnection(this);

            socket.close();
        }

        public boolean isConnected() {
            return socket.isConnected();
        }

        public byte[] getLocalAddress() {
            throw new UnsupportedOperationException();
        }
    }

    /* ************************************************** */

    protected int ACCEPT_TIMEOUT = 100; /* ms */

    private Thread serverThread;

    /** <p>Set the default TCP port. The
     * <code>TCPIPConnectionManager</code> will listen on this port,
     * and it will be used as the default port when connecting to
     * remote hosts.</p>
     *
     * <p>Calling this method has no effect on existing connections.</p>
     *
     * @param port New default TCP port.
     *
     * @see #setListenEnabled(boolean)
     * @see #connectHost(String)
     */
    public synchronized void setTCPPort(int port) {
        if (tcp_port == port) return;
        tcp_port = port;

        /* If necessary, restart the server thread. */
        if (isListenEnabled()) {
            setListenEnabled(false);
            setListenEnabled(true);
        }
    }

    /** Get the default TCP port.
     *
     * @return the default TCP port for new TCP/IP tunnel connections.
     */
    public int getTCPPort() {
        return tcp_port;
    }

    /** <p>Set whether connections are accepted from other devices.<p>
     *
     * <p>If <code>enabled</code> is <code>true</code>, accept
     * incoming connections on the current default TCP port.</p>
     *
     * @param enabled If <code>true</code>, listen for incoming
     *                connections.
     *
     * @see #setTCPPort(int)
     */
    public synchronized void setListenEnabled(boolean enabled) {

        if (isListenEnabled()) {
            serverThread.interrupt();
            try {
                serverThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            serverThread = null;
        } else {
            serverThread = new Thread (new TCPIPServer());

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

    private class TCPIPServer implements Runnable {

        public void run() {
            ServerSocket listen_sock = null;
            try {
                listen_sock = new ServerSocket(tcp_port);
                listen_sock.setSoTimeout(ACCEPT_TIMEOUT);
                while (!Thread.interrupted()) {
                    try {
                        Socket client_sock = listen_sock.accept();

                        /* This *isn't* just being thrown away! */
                        TCPIPConnectionManager.this.new
                            TCPIPConnection(client_sock);
                    } catch (SocketTimeoutException e) {
                        continue;
                    }
                }
            } catch (IOException e) {
                System.err.printf("SPP serverr error: %1$s", e.getMessage());
            } finally {
                if (listen_sock != null) {
                    try {
                        listen_sock.close();
                    } catch (IOException e) { }
                }
            }
        }
    }

    /* ************************************************** */

    /** Create a new <code>TCPIPConnectionManager</code>. Do not call
     * this directly: use <code>getConnectionManager()</code>.
     *
     * @see #getConnectionManager()
     */
    protected TCPIPConnectionManager() {
        serverThread = null;
    }

    /** The singleton instance of the connection manager. */
    private static TCPIPConnectionManager instance = null;

    /** Get the TCP/IP connection manager
     *
     * @return the global <code>TCPIPConnectionManager</code>.
     */
    public static synchronized TCPIPConnectionManager getConnectionManager() {
        if (instance == null) {
            instance = new TCPIPConnectionManager();
        }
        return instance;
    }
}
