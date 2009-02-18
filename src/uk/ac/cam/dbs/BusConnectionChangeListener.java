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

import java.util.EventListener;

/** An event listener for changes to distributed bus connections.
 *
 * @see SystemBus#addConnectionChangeListener(BusConnectionChangeListener)
 * @see SystemBus#removeConnectionChangeListener(BusConnectionChangeListener)
 */

public interface BusConnectionChangeListener extends EventListener {

    /** Status value indicating connection was added */
    static final int CONNECTION_ADDED = 1;
    /** Status value indicating connection was removed */
    static final int CONNECTION_REMOVED = 2;

    /** <p>Handle a connection change event. The <code>status</code> is
     * currently limited to one of:</p>
     *
     * <ul>
     * <li><code>CONNECTION_ADDED</code></li>
     * <li><code>CONNECTION_REMOVED</code></li>
     * </ul>
     *
     * <p>All other possible values for <code>status</code> are reserved.</p>
     *
     * @param connection The connection that changed.
     * @param status     How the connection changed.
     */
    void connectionChanged(BusConnection connection, int status);

}
