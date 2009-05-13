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
 * <h1>Distributed bus system core</h1>
 *
 * <p>This package provides all of the functionality required to
 * connect to other devices and communicate with them. The two most
 * important concepts are the bus context and the connection
 * managers.</p>
 *
 * <p>The <code>SystemBus</code> <em>bus context</em> encapsulates all of the
 * information about the application's connection to the distributed
 * bus. There is only one bus context at any time, and it can be
 * obtained using <code>SystemBus.getSystemBus()</code>.</p>
 *
 * <p>The <em>connection managers</em> create outgoing connections and
 * listen for incoming connections from other nodes on the distributed
 * bus. There is a separate connection manager for each low-level
 * communication protocol supported by the bus. For example, the
 * <code>BluetoothConnectionManager</code> is used to create
 * distributed bus links over Bluetooth.</p>
 *
 * <p>The data link protocol provided by this package is called DMP
 * (short for Datagram Multiplexing Protocol). The protocol uses
 * asynchronous packets sent from and delivered to numbered 'ports' to
 * allow multiple services to share a serial connection. The protocol
 * has no routing capability, handshaking or error-correction; this is
 * expected to be supplied by higher-level protocols. The clock
 * synchronisation service implemented by <code>ClockSync</code> is a
 * good example of an application built on the distributed bus using
 * DMP.</p>
 *
 * @see uk.ac.cam.dbs.SystemBus
 * @see uk.ac.cam.dbs.BluetoothConnectionManager
 * @see uk.ac.cam.dbs.DMPMessage
 */

package uk.ac.cam.dbs;
