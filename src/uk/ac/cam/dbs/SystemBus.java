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

import java.util.Vector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/** <p>Manages global distributed bus state.</p>
 *
 * <p>The single <code>SystemBus</code> instance, obtained via
 * <code>getSystemBus()</code>, contains the global state of the
 * distributed bus system. It provides methods for sending and
 * receiving DMP messages, and registering and removing connections
 * from being managed by the distributed bus.</p>
 *
 * <p>The bus system provides a low-level packet multiplexing service
 * that allows multiple "services" to transparently share the same
 * connections. In order to do this, it runs one thread per
 * <code>BusConnection</code> which polls for incoming messages and
 * dispatches them to the appropriate
 * <code>DMPMessageListener</code>.</p>
 *
 * @see #getSystemBus()
 * @see BusConnection
 * @see DMPMessageListener
 */
public class SystemBus implements DMPMessageListener {

    /** A list of DMP port bindings */
    private Vector portBindings;

    /** Bind a DMP <code>service</code> to a particular DMP
     * <code>port</code>.
     *
     * @param service The service to be notified if a message arrives
     *                for <code>port</code>.
     * @param port    The port number that service wishes to listen on.
     *
     * @throws DMPBindException if the <code>port</code> has already
     *                          been bound.
     */
    public void addDMPService(DMPMessageListener service, int port) throws DMPBindException {
        if (service == null) {
            throw new IllegalArgumentException ("Invalid service");
        }
        /* Check that the port is not already bound */
        synchronized (portBindings) {
            for (int i = 0; i < portBindings.size(); i++) {
                DMPBinding b = (DMPBinding) portBindings.elementAt(i);
                if (port == b.port) {
                    throw new DMPBindException("Port " + Integer.toString(port) +
                                               " in use");
                }
            }
            portBindings.addElement(new DMPBinding(service, port));
        }
    }

    /** Unbind a DMP service from a particular DMP port. After this
     * method is called, <code>service</code> will no longer be
     * notified of messages arriving for <code>port</code>. If
     * <code>port</code> is -1, unbinds the service from <em>all</em>
     * ports.
     *
     * @param service The service to be unbound.
     * @param port    The port to be unbound, or -1 to match all ports.
     */
    public void removeDMPService(DMPMessageListener service, int port) {
        synchronized (portBindings) {
            for (int i = 0; i < portBindings.size(); i++) {
                DMPBinding b = (DMPBinding) portBindings.elementAt(i);
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
        }
    }

    /** Unbind a DMP service from all DMP ports. This is equivalent to
     * calling:
     *
     * <code>removeDMPService(service, -1).</code>
     *
     * @param service The service to be unbound.
     *
     * @see #removeDMPService(DMPMessageListener, int) */
    public void removeDMPService(DMPMessageListener service) {
        removeDMPService(service, -1);
    }

    /** Send a DMP message. Transmits <code>msg</code> on
     * <code>connection</code>.  If <code>connection</code> is
     * <code>null</code>, delivers the message locally.
     *
     * @param connection The connection to transmit on.
     * @param msg        The message to send.
     *
     * @throws IOException if an error occurs while transmitting the
     *                     message.
     */
    public void sendDMPMessage(BusConnection connection, DMPMessage msg)
        throws IOException {
        if (connection == null) {
            recvDMPMessage(connection, msg);
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

    /** Deliver a DMP message. Examines the destination port of
     * <code>msg</code>, and if there has been a service bound to that
     * port, delivers it to the service. If no service has been bound,
     * drops the <code>msg</code> silently.
     *
     * @param connection Connection the <code>msg</code> arrived from.
     * @param msg        Message to deliver.
     *
     * @see DMPMessage#getToPort()
     */
    public void recvDMPMessage(BusConnection connection, DMPMessage msg) {
        synchronized (portBindings) {
            for (int i = 0; i < portBindings.size(); i++) {
                DMPBinding b = (DMPBinding) portBindings.elementAt(i);
                if (msg.getToPort() == b.port) {
                    b.service.recvDMPMessage(connection, msg);
                    return;
                }
            }
            /* No port binding was present, so silently drop the packet */
        }
    }

    /** Binding of DMP service to DMP port */
    private class DMPBinding {
        DMPMessageListener service;
        int port;
        DMPBinding(DMPMessageListener service, int port) {
            this.service = service;
            this.port = port;
        }
    }

    /* ************************************************** */

    /** A list of active connections. */
    private Vector connections;
    /** A list of DMPMonitors */
    private Vector connectionMonitors;
    /** A list of BusConnectionChangeListener. */
    private Vector connectionListeners;

    /** Add a a listener for connection change events. The
     * <code>BusConnectionChangeListener</code>'s
     * <code>connectionChanged()</code> method is called whenever a
     * connection is added or removed from the list of active
     * connections.
     *
     * @param l Event handler to add.
     *
     * @see BusConnectionChangeListener#connectionChanged(BusConnection, int)
     */
    public void addConnectionChangeListener(BusConnectionChangeListener l) {
        synchronized (connectionListeners) {
            if (connectionListeners.indexOf(l) == -1) {
                connectionListeners.addElement(l);
            }
        }
    }

    /** Remove a listener for connection change events. If
     * <code>l</code> was not previously added with
     * <code>addConnectionChangeListener()</code>, does nothing.
     *
     * @param l Event handler to remove.
     */
    public void removeConnectionChangeListener(BusConnectionChangeListener l) {
        synchronized (connectionListeners) {
            connectionListeners.removeElement(l);
        }
    }

    /** Dispatch a connection change event to listeners. */
    private void connectionChangeDispatch(BusConnection c, int s) {
        synchronized (connectionListeners) {
            for (int i = 0; i < connectionListeners.size(); i++) {
                BusConnectionChangeListener l = (BusConnectionChangeListener)
                    connectionListeners.elementAt(i);
                l.connectionChanged(c, s);
            }
        }
    }

    /** <p>Get a list of active connections.</p>
     *
     * <p><b>Warning:</b> the <code>Vector</code> returned by this
     * call is owned by the <code>SystemBus</code> instance, and should
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
            DMPMonitor m = new DMPMonitor(connection);
            (new Thread(m)).start();
            connectionMonitors.addElement(m);
        }

        connectionChangeDispatch(connection,
                                 BusConnectionChangeListener.CONNECTION_ADDED);
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
                DMPMonitor m = (DMPMonitor) connectionMonitors.elementAt(i);
                if (m.conn == connection) {
                    m.shutdown();
                    connectionMonitors.removeElement(m);
                    break;
                }
            }
        }

        connectionChangeDispatch(connection,
                                 BusConnectionChangeListener.CONNECTION_REMOVED);
    }

    /** Monitors a BusConnection for incoming messages */
    private class DMPMonitor implements Runnable {
        BusConnection conn;
        private boolean enabled;
        private Object lock;

        DMPMonitor(BusConnection connection) {
            super();
            conn = connection;
            lock = new Object();
            enabled = true;
        }

        public void run() {
            try {
                InputStream in = conn.getInputStream();
                DataInputStream din = new DataInputStream(in);
                while (true) {
                    synchronized (in) {
                        DMPMessage msg = DMPMessage.recv(din);
                        SystemBus.this.recvDMPMessage(conn, msg);
                    }
                    synchronized (lock) {
                        /* Note that shutdown() is only called from
                         * removeConnection(), so bypass calling it
                         * again. */
                        if (!enabled) return;
                    }
                    Thread.yield();
                }
            } catch (IOException e) {
                try {
                    conn.disconnect();
                } catch (IOException f) {
                    System.err.println("DMPMonitor: Disconnect failed");
                }
            } finally {
                removeConnection(conn);
            }
        }

        void shutdown() {
            synchronized (lock) {
                enabled = false;
            }
        }
    }

    /* ************************************************** */

    private InterfaceAddress mainAddress;

    /** Get the main address of the local bus system node. If no
     * address has been set with <code>setMainAddress()</code>,
     * attempts to automatically determine an address using the first
     * active connection. If no address can be determined, returns
     * <code>null</code>.
     *
     * @return The main address of the bus system node or
     *         <code>null</code> if none can be determined.
     */
    public InterfaceAddress getMainAddress() {
        if (mainAddress == null) {
            try {
                BusConnection c =
                    (BusConnection) connections.elementAt(0);
                mainAddress = c.getLocalAddress();
            } catch (ArrayIndexOutOfBoundsException e) {
                /* No address available */
            }
        }
        return mainAddress;
    }

    /** Set the main address of the bus system node. The address must
     * be unique to the node.
     *
     * @param addr Address to use as new main address.
     */
    public void setMainAddress(InterfaceAddress addr) {
        mainAddress = addr;
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
        connectionListeners = new Vector();
        portBindings = new Vector();
        mainAddress = null;
    }

    /** The singleton instance of the system bus */
    private static SystemBus instance = null;

    /** Get the system bus.
     *
     * @return the global <code>SystemBus</code>.
     */
    public static synchronized SystemBus getSystemBus() {
        if (instance == null) {
            instance = new SystemBus();
        }
        return instance;
    }
}
