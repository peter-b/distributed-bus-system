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

/**
 * <p>A provider of naming services.</p>
 *
 * <p>A <code>NamingProvider</code> associates 128-bit interface
 * addresses with bus node hostnames, and allows applications to convert one to
 * another.</p>
 */
public interface NamingProvider {

    /** Get an <code>InterfaceAddress</code> for a particular hostname.
     *
     * @param deviceName Hostname for which to retrieve an address.
     *
     * @return the <code>InterfaceAddress</code> corresponding to
     *         <code>deviceName</code>, or <code>null</code> if none
     *         is known.
     */
    InterfaceAddress getAddressByName(String deviceName);

    /** Get a hostname for a particular <code>InterfaceAddress</code>
     *
     * @param address <code>InterfaceAddress</code> for which to
     * retrieve a hostname.
     *
     * @return the hostname of the bus node with the given <code>address</code>.
     */
    String getNameByAddress(InterfaceAddress address);
}
