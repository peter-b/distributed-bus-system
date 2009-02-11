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

import java.io.IOException;
import lejos.nxt.LCD;

public class ClockSyncDemo {

    private static final String remoteAddr = "0009DD648148";

    private static char[] buf = new char[21];

    public static void main(String[] args) {

        TimeProvider intTime = TimeProvider.systemTimeProvider();
        ClockSync service = new ClockSync(intTime);

        LCD.clear();

        Thread serviceThread = new Thread(service);
        serviceThread.setDaemon(true);
        serviceThread.start();

        System.out.println("Clock sync started");

        /* Make connection */
        try {
            System.out.println("Connecting " + remoteAddr);

            BluetoothConnectionManager bt =
                BluetoothConnectionManager.getConnectionManager();

            bt.connectDevice(remoteAddr);

        } catch (IOException e) {
            if (e.getMessage() != null)
                System.out.println(e.getMessage());
            System.out.println("Connection failed!");
            try { Thread.sleep(1000); } catch (InterruptedException f) { }
            return;
        }

        System.exit(0);

        while (true) {
            try {
                Thread.sleep (1000);
            } catch (InterruptedException e) {
                System.out.println("Interrupted");
            }
        }
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
