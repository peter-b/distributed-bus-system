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

/**
 * <h1>Simplified Flood Routing Protocol (SFRP)</h1>
 *
 * <p>The simplified flood routing protocol is a very simple and
 * inefficient route-finding protocol that detects active end-to-end
 * routes by a message flooding algorithm.</p>
 *
 * <p>The protocol uses DMP port 50054.</p>
 *
 * <h2>Using the protocol</h2>
 *
 * <p>The following code snippet provides an example of starting a
 * SFRP service:</p>
 *
 * <pre>
 * InterfaceAddress addr = &lt;get main address for device&gt;;
 * SimplifiedFloodRouting routingService = new SimplifiedFloodRouting(addr);
 * routingService.start();
 * </pre>
 *
 * <p>The service has a mechanism for notifying application code when
 * a route changes, using the <code>SfrpRouteChangeListener</code>
 * interface. For example:</p>
 *
 * <pre>
 * SfrpRouteChangeListener notifier =
 *     new SfrpRouteChangeListener() {
 *         public void routeChanged (InterfaceAddress addr, int status) {
 *             String message = "# Route to [" + addr.toString() + "] ";
 *             if (status == ROUTE_ADDED) {
 *                 message = message + "UP";
 *             } else if (status == ROUTE_REMOVED) {
 *                 message = message + "DOWN";
 *             } else {
 *                 message = message + "CHANGED";
 *             }
 *             System.out.println (message);
 *         }
 *     };
 * routingService.addRouteChangeListener(notifier);
 * </pre>
 *
 * <h2>Routing algorithm</h2>
 * <p>The approach works as follows:</p>
 *
 * <ol>
 * <li>Each device periodically transmits a HELLO message on all
 * available interfaces.</li>
 * <li>On receiving a HELLO message, a device compares the message's
 * sequence number and main address to the record of previous messages
 * received. If a message with that sequence number has already been
 * received, the message is ignored.
 * <li>The message's hop count is incremented, and the message is then
 * forwarded over every connection other than the one it was received
 * from.</li>
 * <li>The message information is stored in the record of received
 * messages and used for routing lookups.</li>
 * <li>After the validity period specified in the HELLO message
 * expires, the route is marked as dead.</li>
 * </ol>
 *
 * <p>Note that this algorithm only provides "next hop" routing
 * information. It does not detect multiple paths or intermittent
 * connections, and does not allow for load balancing or link
 * weighting.</p>
 *
 * <h2>HELLO message structure</h2>
 * <table>
 * <thead>
 * <tr><th>Field size (bits)</th><th>Field description</th></tr>
 * </thead>
 * <tbody>
 * <tr><td>16</td><td>Message sequence number</td></tr>
 * <tr><td>16</td><td>Hop count</td></tr>
 * <tr><td>16</td><td>Validity period (ms)</td></tr>
 * <tr><td>16</td><td>(Reserved)</td></tr>
 * <tr><td>128</td><td>Device main address</td></tr>
 * </tbody>
 * </table>
 *
 * <p>The message sequence number must be monotonically increasing, and
 * should wrap around to zero. The hop count must be initialised to zero.</p>
 *
 * @see uk.ac.cam.dbs.InterfaceAddress
 * @see uk.ac.cam.dbs.DMPMessage
 */

package uk.ac.cam.dbs.sfrp;
