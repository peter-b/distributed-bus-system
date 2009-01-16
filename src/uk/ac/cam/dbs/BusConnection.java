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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** A connection to another device.
 */
public interface BusConnection {

    /** Get the local address of the connection. Used to identify a
     * connection by remote devices.
     *
     * @return The local interface address.
     */
    byte[] getLocalAddress();

    /** Tests if this <code>BusConnection</code> is connected.
     *
     * @return <code>true</code> if the connection is active.
     */
    boolean isConnected();

    /** Disconnects the connection.
     *
     * @throws <code>IOException</code> if an error occurs during
     *         disconnection.
     */
    void disconnect() throws IOException;

    /** <p>Gets an <code>InputStream</code> for the connection.</p>
     *
     * <p>Implementations should ensure that repeated calls to this
     * function return an equivalent stream. If different streams are
     * returned, they must be thread-safe.</p>
     *
     * @return a <code>InputStream</code> for receiving data from the
     *         remote device.
     */
    InputStream getInputStream() throws IOException;

    /** <p>Gets an <code>OutputStream</code> for the connection.</p>
     *
     * <p>Implementations should ensure that repeated calls to this
     * function return an equivalent stream. If different streams are
     * returned, they must be thread-safe.</p>
     *
     * @return a <code>OutputStream</code> for sending data to the
     *         remote device.
     */
    OutputStream getOutputStream() throws IOException;
}
