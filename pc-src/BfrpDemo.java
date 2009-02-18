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

import uk.ac.cam.dbs.*;
import uk.ac.cam.dbs.bfrp.*;

import java.util.Random;
import java.util.Vector;
import java.util.Iterator;
import java.util.ConcurrentModificationException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class BfrpDemo
    implements BusConnectionChangeListener, BfrpRouteChangeListener {

    boolean isDaemon;

    public static void main(String[] args) {
        BfrpDemo demo = new BfrpDemo();
        demo.isDaemon = (args.length < 1);

        SystemBus.getSystemBus().addConnectionChangeListener(demo);

        TCPIPConnectionManager tcpip
            = TCPIPConnectionManager.getConnectionManager();
        InterfaceAddress mainAddress = tcpip.getLocalAddress();

        /* Start BFRP thread */
        System.out.println("# Main address: " + mainAddress);
        BruteForceRouting routingService = new BruteForceRouting(mainAddress);
        routingService.addRouteChangeListener(demo);
        Thread bfrpThread = new Thread(routingService);
        //        bfrpThread.setDaemon(true);
        bfrpThread.start();

        /* Make connection */
        try {
            if (!demo.isDaemon) {
                System.out.println("# Connecting...");
                tcpip.connectHost(args[0]);
            } else {
                System.out.println("# Listening for connections...");
                tcpip.setListenEnabled(true);
            }
        } catch (IOException e) {
            System.err.printf("Failed to open connection: %1$s\n",
                              e.getMessage());
            return;
        }

        /* The program exits when the BFRP thread does. */
    }

    public void routeChanged (InterfaceAddress addr, int status) {
        String message = "# Route to [" + addr.toString() + "] ";
        if (status == ROUTE_ADDED) {
            message = message + "UP";
        } else if (status == ROUTE_REMOVED) {
            message = message + "DOWN";
        } else {
            message = message + "CHANGED";
        }
        System.out.println (message);
    }

    public void connectionChanged (BusConnection conn, int status) {
        String message = "# Link to [" + conn.getRemoteAddress().toString() + "] ";
        if (status == CONNECTION_ADDED) {
            message = message + "UP";
        } else if (status == CONNECTION_REMOVED) {
            message = message + "DOWN";
        } else {
            message = message + "CHANGED";
        }
        System.out.println (message);
    }
}
