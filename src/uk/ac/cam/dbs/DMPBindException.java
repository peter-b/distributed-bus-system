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

/** Signals that an error occurred while attempting to bind a service
 * to to a local DMP port.
 */
public class DMPBindException extends java.io.IOException {

    /** Construct a new <code>DMPBindException</code> with no detailed
     * message. */
    public DMPBindException() {
        super();
    }

    /** Constructs a new <code>DMPBindException</code> with the
     * specified detail message as to why the bind error occurred. */
    public DMPBindException(String message) {
        super(message);
    }
}
