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

import java.util.EventListener;
import uk.ac.cam.dbs.*;

/** An event listener for BFRP route changes.
 * @see BruteForceRouting#addRouteChangeListener(BfrpRouteChangeListener)
 * @see BruteForceRouting#removeRouteChangeListener(BfrpRouteChangeListener)
 */
public interface BfrpRouteChangeListener extends EventListener {

    /** Status value indicating route was added */
    static final int ROUTE_ADDED = 1;

    /** Status value indicating route was removed */
    static final int ROUTE_REMOVED = 2;

    /** <p>Handle a route change event. The <code>status</code> is
     * currently limited to one of:</p>
     *
     * <ul>
     * <li><code>ROUTE_ADDED</code></li>
     * <li><code>ROUTE_REMOVED</code></li>
     * </ul>
     *
     * <p>All other possible values for <code>status</code> are
     * reserved.</p>
     *
     * @param node   Main address of the network node for which
     *               routing changed.
     * @param status How the route changed.
     */
    void routeChanged(InterfaceAddress node, int status);
}
