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

/** Base class for clock providers. */
public class TimeProvider {

    private static TimeProvider systemTime = null;

    /** Get the system time provider.
     *
     * @return a singleton <code>TimeProvider</code> instance that
     *         provides the system time.
     */
    public static synchronized TimeProvider systemTimeProvider() {
        if (systemTime == null) {
            systemTime = new TimeProvider();
        }
        return systemTime;
    }

    /** Create a new TimeProvider.
     *
     * The base implementation simply provides the system time.
     */
    public TimeProvider() {
        super();
    }

    /** Get the current time in milliseconds.
     *
     * The base implementation simply calls
     * <code>System.currentTimeMillis()</code>.
     *
     * @return the current time in milliseconds.
     *
     * @see java.lang.System#currentTimeMillis()
     */
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

}
