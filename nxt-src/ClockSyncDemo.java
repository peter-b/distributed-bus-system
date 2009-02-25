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

import java.io.IOException;
import lejos.nxt.LCD;

public class ClockSyncDemo {

    private static char[] buf = new char[21];

    public static void main(String[] args) {

        LCD.clear();

        /* Get the Bluetooth connection manager */
        BluetoothConnectionManager bt = null;
        try {
            bt = BluetoothConnectionManager.getConnectionManager();
        } catch (Exception e) {
            throw new RuntimeException();
        }

        /* Start up clock sync service */
        TimeProvider intTime = TimeProvider.systemTimeProvider();
        ClockSync clockService = new ClockSync(intTime);
        Thread clockThread = new Thread(clockService);
        clockThread.setDaemon(true);
        clockThread.start();

        /* Start up routing service */
        BruteForceRouting routingService =
            new BruteForceRouting(bt.getLocalAddress());
        BfrpRouteChangeListener notifier =
            new BfrpRouteChangeListener() {
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
            };
        routingService.addRouteChangeListener(notifier);
        Thread routeThread = new Thread(routingService);
        routeThread.setDaemon(true);
        routeThread.start();

        /* Make connection */
        bt.setListenEnabled(true);

        try {
            while (true) {
                System.out.println(longToString(clockService.currentTimeMillis()));
                Thread.sleep(1000);
            }
        } catch (InterruptedException f) { }
    }

    public static String longToString(long l) {
        boolean isNegative = (l < 0);
        if (l == -9223372036854775808L)
            return "-9223372036854775808";
        int i = buf.length;
        long v = isNegative ? -l : l;
        while (v != 0) {
            char c = '0';
            int dec = (int) (v % 10L);
            switch (dec) {
            case 9:  c = '9'; break;
            case 8:  c = '8'; break;
            case 7:  c = '7'; break;
            case 6:  c = '6'; break;
            case 5:  c = '5'; break;
            case 4:  c = '4'; break;
            case 3:  c = '3'; break;
            case 2:  c = '2'; break;
            case 1:  c = '1'; break;
            case 0:  c = '0'; break;
            default: c = '?';
            }
            buf[--i] = c;
            v = v / 10;
        }
        if (isNegative) {
            buf[--i] = '-';
        }
        return new String(buf, i, buf.length - i);
    }
}
