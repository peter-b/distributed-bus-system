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

import java.util.Random;
import java.util.Vector;
import java.util.Iterator;
import java.util.ConcurrentModificationException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class TCPIPConnectionDemo implements BusConnectionChangeListener {

    boolean isDaemon;

    static final int NUM_THREADS = 3;
    static final int RUN_TIME = 30000; /* ms */

    public static void main(String[] args) {
        TCPIPConnectionDemo demo = new TCPIPConnectionDemo();
        demo.isDaemon = (args.length < 1);

        SystemBus.getSystemBus().addConnectionChangeListener(demo);

        /* Start threads */
        for (int i = 1; i <= NUM_THREADS; i++) {
            Thread t = new Thread(demo.new DummyService(i));
            t.setDaemon(true);
            t.start();
        }

        /* Make connection */
        try {
            TCPIPConnectionManager tcpip =
                TCPIPConnectionManager.getConnectionManager();
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

        try {
            Thread.sleep(RUN_TIME);
        } catch (InterruptedException e) {
            return; /* Just quit */
        }
    }

    public void connectionChanged(BusConnection connection, int status) {
        String message = "# Link to [" + connection.getRemoteAddress().toString() + "] ";
        if (status == CONNECTION_ADDED) {
            message = message + "UP";
        } else if (status == CONNECTION_REMOVED) {
            message = message + "DOWN";
        } else {
            message = message + "CHANGED";
        }
        System.out.println (message);
    }

    private class DummyService
        implements Runnable, UDPMessageListener {

        String type;
        int port;
        static final int LOOP_DELAY = 5000; /* ms */

        DummyService(int port) {
            if (isDaemon) {
                type = "server";
            } else {
                type = "client";
            }
            this.port = port;
        }

        public void run() {
            try {
                SystemBus.getSystemBus().addUDPService(this, port);

                Random r = new Random();
                Thread.sleep(r.nextInt(LOOP_DELAY));

                int seq = 1;

                while (true) {
                    /* Send a message on each connection */
                    Vector conn = SystemBus.getSystemBus().getConnections();
                    Iterator i = conn.iterator();
                    try {
                        while (i.hasNext()) {
                            String s;
                            byte[] payload;
                            try {
                                s = String.format("%1$s-%2$d", type, seq++);
                                payload = s.getBytes("UTF-8");
                            } catch (UnsupportedEncodingException e) {
                                System.err.printf(e.getMessage());
                                s = "";
                                payload = new byte[0];
                            }
                            UDPMessage m = new UDPMessage(port, port, payload);

                            BusConnection c = (BusConnection) i.next();
                            SystemBus.getSystemBus().sendUDPMessage(c, m);
                            System.out.printf("Sent \"%1$s\" on port %2$d\n",
                                              s, port);
                        }
                    } catch (ConcurrentModificationException e) {
                        /* Doesn't matter, wait until next time around */
                    } catch (IOException e) {
                        System.err.printf("Send failed: %1$s\n", e.getMessage());
                    }

                    Thread.sleep(LOOP_DELAY);
                }
            } catch (UDPBindException e) {
                System.err.printf("Failed to bind UDP port: %1$s\n", e.getMessage());
            } catch (Exception e) {
                /* Yawn */
            } finally {
                SystemBus.getSystemBus().removeUDPService(this, port);
            }
        }

        public void recvUDPMessage(BusConnection c, UDPMessage m) {
            String message;
            try {
                message = new String(m.getPayload(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                System.err.printf(e.getMessage());
                return;
            }
            System.out.printf("Received \"%1$s\" on port %2$d\n",
                              message, m.getToPort());
        }
    }
}
