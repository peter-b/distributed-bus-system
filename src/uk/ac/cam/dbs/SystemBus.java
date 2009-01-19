package uk.ac.cam.dbs;

import java.util.Vector;
import java.net.BindException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/** Manages global distributed bus state.
 *
 * The single <code>SystemBus</code> instance, obtained via
 * <code>getSystemBus()</code>, contains the global state of the
 * distributed bus system. It provides methods for sending and
 * receiving UDP messages, and registering and removing connections
 * from being managed by the distributed bus.
 *
 * The bus system provides a low-level packet multiplexing service
 * that allows multiple "services" to transparently share the same
 * connections. In order to do this, it runs one thread per
 * <code>BusConnection</code> which polls for incoming messages and
 * dispatches them to the appropriate <code>UDPMessageListener</code>.
 *
 * @see #getSystemBus()
 * @see BusConnection
 * @see UDPMessageListener
 */
public class SystemBus implements UDPMessageListener {

    /** A list of UDP port bindings */
    private Vector portBindings;

    /** Bind a UDP <code>service</code> to a particular UDP
     * <code>port</code>.
     *
     * @param service The service to be notified if a message arrives
     *                for <code>port</code>.
     * @param port    The port number that service wishes to listen on.
     *
     * @throws BindException if the <code>port</code> has already been bound.
     * */
    public void addUDPService(UDPMessageListener service, int port) throws BindException {
        if (service == null) {
            throw new IllegalArgumentException ("Invalid service");
        }
        /* Check that the port is not already bound */
        synchronized (portBindings) {
            for (int i = 0; i < portBindings.size(); i++) {
                UDPBinding b = (UDPBinding) portBindings.elementAt(i);
                if (port == b.port) {
                    throw new BindException("Port in use");
                }
            }
            portBindings.addElement(new UDPBinding(service, port));
            portBindings.size(); /* Magic */
        }
    }

    /** Unbind a UDP service from a particular UDP port. After this
     * method is called, <code>service</code> will no longer be
     * notified of messages arriving for <code>port</code>. If
     * <code>port</code> is -1, unbinds the service from <em>all</em>
     * ports.
     *
     * @param service The service to be unbound.
     * @param port    The port to be unbound, or -1 to match all ports.
     */
    public void removeUDPService(UDPMessageListener service, int port) {
        synchronized (portBindings) {
            for (int i = 0; i < portBindings.size(); i++) {
                UDPBinding b = (UDPBinding) portBindings.elementAt(i);
                if (service == b.service) {
                    /* If the port was specified, only remove that
                     * particular binding */
                    if (port == b.port) {
                        portBindings.removeElement(b);
                        break;
                    }
                    /* If the port was not specified, remove every
                     * binding of this service */
                    if (port < 0) {
                        portBindings.removeElement(b);
                        continue;
                    }
                }
            }
            portBindings.size(); /* Magic */
        }
    }

    /** Unbind a UDP service from all UDP ports. This is equivalent to
     * calling:
     *
     * <code>removeUDPService(service, -1).</code>
     *
     * @param service The service to be unbound.
     *
     * @see #removeUDPService(UDPMessageListener, int) */
    public void removeUDPService(UDPMessageListener service) {
        removeUDPService(service, -1);
    }

    /** Send a UDP message. Transmits <code>msg</code> on
     * <code>connection</code>.  If <code>connection</code> is
     * <code>null</code>, delivers the message locally.
     *
     * @param connection The connection to transmit on.
     * @param msg        The message to send.
     *
     * @throws IOException if an error occurs while transmitting the
     *                     message.
     */
    public void sendUDPMessage(BusConnection connection, UDPMessage msg)
        throws IOException {
        if (connection == null) {
            recvUDPMessage(connection, msg);
            return;
        }

        /* Pass message into connection */
        try {
            OutputStream out = connection.getOutputStream();
            synchronized (out) {
                msg.send(new DataOutputStream(out));
            }
        } catch (IOException e) {
            connection.disconnect();
            throw e;
        }
    }

    /** Deliver a UDP message. Examines the destination port of
     * <code>msg</code>, and if there has been a service bound to that
     * port, delivers it to the service. If no service has been bound,
     * drops the <code>msg</code> silently.
     *
     * @param connection Connection the <code>msg</code> arrived from.
     * @param msg        Message to deliver.
     *
     * @see UDPMessage#getToPort()
     */
    public void recvUDPMessage(BusConnection connection, UDPMessage msg) {
        synchronized (portBindings) {
            for (int i = 0; i < portBindings.size(); i++) {
                UDPBinding b = (UDPBinding) portBindings.elementAt(i);
                if (msg.getToPort() == b.port) {
                    b.service.recvUDPMessage(connection, msg);
                    return;
                }
            }
            /* No port binding was present, so silently drop the packet */
        }
    }

    /** Binding of UDP service to UDP port */
    private class UDPBinding {
        UDPMessageListener service;
        int port;
        UDPBinding(UDPMessageListener service, int port) {
            this.service = service;
            this.port = port;
        }
    }

    /* ************************************************** */

    /** A list of active connections. */
    private Vector connections;
    private Vector connectionMonitors;

    /** <p>Get a list of active connections.</p>
     *
     * <p><b>Warning:</b> the <code>Vector</code> returned by this
     * call is owned by the <code>SystemBus<code> instance, and should
     * not be modified.</p>
     *
     * @return a <code>Vector</code> of
     *         <code>BusConnection</code>s.
     */
    public Vector getConnections() {
        return connections;
    }

    /** Add a connection to the active connections. Starts a thread
     * which monitors <code>connection</code>'s
     * <code>InputStream</code> for incoming messages, and dispatches
     * them when they arrive, until the connection is removed.
     *
     * @param connection Connection to add.
     */
    public void addConnection(BusConnection connection) {
        if (connection == null)
            throw new NullPointerException();

        synchronized (connections) {
            for (int i = 0; i < connections.size(); i++) {
                if (connection == connections.elementAt(i)) {
                    return;
                }
            }
            connections.addElement(connection);
        }

        synchronized (connectionMonitors) {
            UDPMonitor m = new UDPMonitor(connection);
            (new Thread(m)).start();
            connectionMonitors.addElement(m);
        }
    }

    /** Remove a connection from the active connections. Halts the
     * monitoring thread.
     *
     * Note that this does <em>not</em> call
     * <code>connection.disconnect()</code>.
     *
     * @param connection Connection to remove.
     *
     * @see BusConnection#disconnect()
     */
    public void removeConnection(BusConnection connection) {
        if (connection == null)
            throw new NullPointerException();

        /* Remove the connection from the list of active
         * connections. */
        synchronized (connections) {
            connections.removeElement(connection);
        }

        synchronized (connectionMonitors) {
            for (int i = 0; i < connectionMonitors.size(); i++) {
                UDPMonitor m = (UDPMonitor) connectionMonitors.elementAt(i);
                if (m.conn == connection) {
                    m.shutdown();
                    connectionMonitors.removeElement(m);
                    break;
                }
            }
        }
    }

    /** Monitors a BusConnection for incoming messages */
    private class UDPMonitor implements Runnable {
        BusConnection conn;
        private boolean enabled;
        private Integer mutex;

        UDPMonitor(BusConnection connection) {
            super();
            conn = connection;
            mutex = new Integer(0);
            enabled = true;
        }

        public void run() {
            try {
                InputStream in = conn.getInputStream();
                DataInputStream din = new DataInputStream(in);
                while (true) {
                    synchronized (in) {
                        UDPMessage msg = UDPMessage.recv(din);
                        SystemBus.this.recvUDPMessage(conn, msg);
                    }
                    synchronized (mutex) {
                        /* Note that shutdown() is only called from
                         * removeConnection(), so bypass calling it
                         * again. */
                        if (!enabled) return;
                    }
                    Thread.yield();
                }
            } catch (IOException e) {
                /* Die */
            } finally {
                removeConnection(conn);
            }
        }

        void shutdown() {
            synchronized (mutex) {
                enabled = false;
            }
        }
    }

    /* ************************************************** */

    /** Create a new system bus. Do not call this directly: use
     * getSystemBus().
     *
     * @see #getSystemBus()
     */
    protected SystemBus() {
        connections = new Vector();
        connectionMonitors = new Vector();
        portBindings = new Vector();
    }

    /** The singleton instance of the system bus */
    private static SystemBus instance = null;

    /** Get the system bus.
     *
     * @return the global <code>SystemBus</code>.
     */
    public static SystemBus getSystemBus() {
        if (instance == null) {
            instance = new SystemBus();
        }
        return instance;
    }
}
