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

package uk.ac.cam.dbs.bundle;

import java.util.Vector;
import java.io.IOException;

import uk.ac.cam.dbs.*;

/** Agent for storing, forwarding and delivering bundles. */
public class BundleAgent
    implements Runnable {

    private static final int BUNDLE_DEFER = 1 << 1;

    private static final int MAX_BUNDLES = 32;
    private static final int DMP_PORT = 4556;
    private static final int DEFER_TIME_MS = 1000;

    private static final String NULL_ENDPOINT = "dtn:none";

    private Vector bundleQueue;
    private Vector endpointRegistrations;

    private Object queueMonitorNotifier;

    private TimeProvider localTime;
    private TimeProvider networkTime;
    private RoutingProvider routing;
    private Object routingLock;

    private BundleDmpService dmpService;

    private long lastTimestamp;
    private int lastSeq;
    private Object timestampLock;

    public BundleAgent() {
        bundleQueue = new Vector(MAX_BUNDLES);
        endpointRegistrations = new Vector();
        queueMonitorNotifier = new Object();

        localTime = TimeProvider.systemTimeProvider();
        /* Use the system clock by default. */
        networkTime = TimeProvider.systemTimeProvider();
        /* Use a "dummy" routing provider by default, that fails for
         * any address. */
        routing = new RoutingProvider() {
                public BusConnection nextHop(InterfaceAddress dest) {
                    return null;
                }
            };
        routingLock = new Object();

        timestampLock = new Object();

        dmpService = this.new BundleDmpService();
    }

    /** <p>Set the routing provider to be used when forwarding
     * bundles.</p>
     *
     * <p>The default routing provider fails to generate a route to
     * any address.</p>
     *
     * @param routing Routing information provider.
     */
    public void setRoutingProvider(RoutingProvider routing) {
        synchronized (routingLock) {
            this.routing = routing;
        }
    }

    /** <p>Get the routing provider used when forwarding bundles.</p>
     *
     * @return Routing information provider.
     */
    public RoutingProvider getRoutingProvider() {
        return routing;
    }

    /** <p>Set the network time provider. This is used when generating
     * bundle timestamps, and when determining whether bundles have
     * expired.</p>
     *
     * <p>The default time provider is the system time provider.</p>
     *
     * @see TimeProvider#systemTimeProvider()
     *
     * @param time Time provider for network time.
     */
    public void setTimeProvider(TimeProvider time) {
        networkTime = time;
    }

    /** <p>Get the network time provider.</p>
     *
     * @return Time provider for network time.
     */
    public TimeProvider getTimeProvider() {
        return networkTime;
    }

    /** Register an endpoint. Once the <code>endpoint</code> is
     * registered, the agent will deliver incoming bundles addressed
     * to that endpoint to the registered <code>listener</code> (as
     * well as notifying of any other endpoint events).
     *
     * @param endpoint  Endpoint to register.
     * @param listener  Event listener to be notified of endpoint events.
     */
    public void registerEndpoint(String endpoint, EndpointEventListener listener) {
        if ((listener == null) || (endpoint == null))
            throw new NullPointerException();

        /* Don't allow binding to null endpoint! */
        if (endpoint.equals(NULL_ENDPOINT)) {
            throw new IllegalArgumentException("Cannot register null endpoint!");
        }

        synchronized (endpointRegistrations) {
            for (int i = 0; i < endpointRegistrations.size(); i++) {
                EndpointRegistration reg =
                    (EndpointRegistration) endpointRegistrations.elementAt(i);
                if (reg.endpoint.equals(endpoint)) {
                    /* We don't currently allow more than one
                     * registration of each endpoint, although the RFC
                     * permits it. */
                    System.err.println("[BUG]: Unable to complete duplicate registration: " + endpoint);
                    return;
                }
            }
            EndpointRegistration reg = this.new EndpointRegistration(endpoint, listener);
            endpointRegistrations.addElement(reg);
        }
    }

    /** Unregister an endpoint.
     *
     * @param endpoint  Endpoint to unregister.
     * @param listener  Event listener to remove from the endpoint.
     */
    public void unregisterEndpoint(String endpoint, EndpointEventListener listener) {
        if ((listener == null) || (endpoint == null))
            throw new NullPointerException();

        synchronized (endpointRegistrations) {
            for (int i = 0; i < endpointRegistrations.size(); i++) {
                EndpointRegistration reg =
                    (EndpointRegistration) endpointRegistrations.elementAt(i);
                if ((reg.endpoint.equals(endpoint)) && (reg.listener == listener)) {
                    endpointRegistrations.removeElementAt(i);
                    return;
                }
            }
        }
    }

    /** Transmit a bundle. Sets the bundle timestamp and sequence
     * number, and queues the bundle for processing and transmission.
     *
     * @param b Bundle to transmit.
     */
    public void sendBundle(Bundle b) {
        long netTime = networkTime.currentTimeMillis()/1000;
        /* Generate timestamp & sequence number. Restart sequence
         * numbers every time timestamp changes. */
        synchronized (timestampLock) {
            b.setTimestamp(netTime);
            if (netTime == lastTimestamp) {
                b.setSequence(++lastSeq);
            } else {
                b.setSequence(0);
                lastSeq = 0;
                lastTimestamp = netTime;
            }
        }
        queueBundle(b);
    }

    /** Carries out necessary actions on a newly-arrived bundle, and
     * adds it to the queue. */
    private void queueBundle(Bundle b) {
        /* If queue 80% full:
         * - If custody transfer requested, generate and add to queue
         *   a custody signal indicating depleted storage. Note that
         *   this may make the queue too long!
         * - Otherwise drop bundle silently.
         */
        if (bundleQueue.size() >= MAX_BUNDLES*0.8) return;

        /* If reporting of bundle reception and/or custody change
         * is requested, generate and add to queue an appropriate
         * status report. */

        /* If custody transfer is requested, generate and add to queue
         * an appropriate custody signal, iff we can actually
         * understand where the bundle needs to be delivered to. */

        /* Add bundle to queue. FIXME check if bundle is already in queue. */
        BundleRecord rec = this.new BundleRecord(b);
        bundleQueue.addElement(rec);

        /* Wake up processing thread. */
        synchronized (queueMonitorNotifier) {
            queueMonitorNotifier.notifyAll();
        }
    }

    public void run() {
        try {
            SystemBus.getSystemBus().addDMPService(dmpService, DMP_PORT);
        } catch (DMPBindException e) {
            System.err.println("Could not bind DMP port: " + e.getMessage());
            return;
        }

        while (true) {
            /* Process bundles */
            long timer = (1L << 63) ^ -1; /* Max long */
            for (int i = 0; i < bundleQueue.size(); i++) {
                BundleRecord rec = (BundleRecord) bundleQueue.elementAt(i);
                processBundle(rec);
                if (rec.status == 0) {
                    /* Finished with bundle, so delete record. */
                    bundleQueue.removeElementAt(i--);
                    continue;
                }
                timer = (timer > rec.timer) ? rec.timer : timer;
            }

            /* Go to sleep for a while */
            long sleepTime = timer - localTime.currentTimeMillis();
            try {
                synchronized (queueMonitorNotifier) {
                    /* If there are bundles in the queue, sleep until the
                     * earliest timer expires. If there are no bundles in
                     * the queue, sleep until woken up. */
                    if (bundleQueue.size() > 0) {
                        if (sleepTime > 0) {
                            queueMonitorNotifier.wait(sleepTime);
                        }
                    } else {
                        queueMonitorNotifier.wait(0);
                    }
                }
            } catch (InterruptedException e) {
                /* Just continue; we get interrupted if e.g. a new bundle
                 * arrives to be processed. */
            }
        }
    }

    /** Process a bundle from the queue. If a bundle is completed, and
     * need no longer be retained, return true; otherwise return
     * false. */
    private void processBundle(BundleRecord rec) {
        long nowLocal = localTime.currentTimeMillis();
        long nowNetwork = networkTime.currentTimeMillis();

        /* If bundle was deferred and has active timer, don't do
         * anything with it. If timer has expired, clear defer
         * flag. */
        if ((rec.status & BUNDLE_DEFER) != 0) {
            if (rec.timer > nowLocal) {
                return;
            } else {
                rec.status &= (~BUNDLE_DEFER);
            }
        }

        /* If the bundle expired, delete it. */
        if ((rec.bundle.getTimestamp() + rec.bundle.getLifetime() < nowNetwork/1000)) {
            /* FIXME generate any necessary reports for deletion. */
            rec.status = 0;
            return;
        }

        String dest = rec.bundle.getDestEndpoint();

        /* Check if we can deliver the bundle to a local endpoint
         * registration. */
        synchronized (endpointRegistrations) {
            for (int i = 0; i < endpointRegistrations.size(); i++) {
                EndpointRegistration r =
                    (EndpointRegistration) endpointRegistrations.elementAt(i);
                if (r.endpoint.equals(dest)) {
                    /* Deliver bundle */
                    r.listener.deliverBundle(rec.bundle);
                    /* FIXME generate any necessary reports for delivery */
                    /* Clear status */
                    rec.status = 0;
                    return;
                }
            }
        }

        /* See if we can work out where the bundle needs to be forwarded to. */
        InterfaceAddress forwardTo = getEndpointHostAddress(dest);
        /* If we can't, then delete the bundle (because we won't be
         * able to do anything with it). */
        if (forwardTo == null) {
            /* FIXME generate any necessary reports for deletion. */
            /* Clear status */
            rec.status = 0;
            return;
        }
        /* If we can get a route, forward the bundle. */
        BusConnection forwardConnection = routing.nextHop(forwardTo);
        if (forwardConnection != null) {
            DMPMessage msg = new DMPMessage(DMP_PORT, rec.bundle.toBytes());
            try {
                SystemBus.getSystemBus().sendDMPMessage(forwardConnection, msg);
                /* FIXME generate any necessary reports for forwarding. */
                /* FIXME custody transfer. */
                /* Clear status */
                rec.status = 0;
                return;
            } catch (IOException e) {
                /* Fall through to defer. */
            }
        }

        /* If we get this far, defer bundle for a while. */
        rec.status |= BUNDLE_DEFER;
        rec.timer = nowLocal + DEFER_TIME_MS;
    }

    private class BundleRecord {
        Bundle bundle;
        int status;
        long timer;
        BundleRecord(Bundle b) {
            this.bundle = b;
            long timer = 0;
        }
    }

    private class EndpointRegistration {
        String endpoint;
        EndpointEventListener listener;
        EndpointRegistration(String endpoint, EndpointEventListener listener) {
            this.endpoint = endpoint;
            this.listener = listener;
        }
    }

    private class BundleDmpService implements DMPMessageListener {
        /** Handles a bundle arriving over DMP */
        public void recvDMPMessage(BusConnection connection, DMPMessage msg) {
            /* Assume one DMP message per bundle */

            /* If queue 100% full: drop bundle silently */
            if (bundleQueue.size() >= MAX_BUNDLES) return;

            /* Parse bundle. */
            Bundle b = new Bundle(msg.getPayload());

            /* Queue bundle */
            queueBundle(b);
        }
    }

    /* ***************************************** */
    /* This stuff should probably be broken out into a separate class */

    private static InterfaceAddress getEndpointHostAddress(String endpoint) {
        String host = getEndpointHostPart(endpoint);
        int hostLen = host.length();

        InterfaceAddress result;

        /* If it looks like a literal address, try that. */
        if ((host.charAt(0) == '[') && (host.charAt(hostLen - 1) == ']')) {
            try {
                return new InterfaceAddress(host.substring(1, hostLen-1));
            } catch (NumberFormatException e) {
                /* A host part with '[' and ']' can't be a hostname. */
                return null;
            }
        }

        /* Otherwise, assume it's a hostname. */
        /* FIXME We need to have some sort of name resolution system! */
        System.err.println("[BUG]: No way to resolve hostname: " + host);
        return null;
    }

    private static String getEndpointHostPart(String endpoint) {
        /* Endpoint should be in format:
         *
         *    dtn://<host part>/<service part>
         *
         * Where host part can be of RFC1035 hostname form:
         *
         *    [a-zA-Z0-9][-a-zA-Z0-9]*
         *
         * Or a hexadecimal numeric address in the form:
         *
         *    \[([a-fA-F0-9]{1,4}:){7}[a-fA-F0-9]{1,4}\]
         */
        if (!endpoint.substring(0, 6).equals("dtn://"))
            return null;

        /* Check if there is, in fact, a service part */
        int hostEnd = endpoint.indexOf('/', 6);
        String host = (hostEnd > 0) ? endpoint.substring(6,hostEnd) : endpoint.substring(6);

        return host;
    }
}
