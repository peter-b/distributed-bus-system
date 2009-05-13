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
import uk.ac.cam.dbs.sfrp.*;

import gnu.getopt.Getopt;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import javax.bluetooth.LocalDevice;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.Timer;

public class ClockSyncDemo extends JFrame
    implements BusConnectionChangeListener, SfrpRouteChangeListener {

    TimeProvider networkTime;
    TimeProvider internalTime;

    JLabel offsetLabel;
    JLabel netTimeLabel;
    JLabel intlTimeLabel;

    public static void main(String[] args) {

        ClockSyncOptions opts = new ClockSyncOptions(args);
        final long offset = opts.initOffset;

        TimeProvider intTime = new TimeProvider() {
                public long currentTimeMillis() {
                    return System.currentTimeMillis() + offset;
                }
            };

        /* Start clock sync service */
        ClockSync service = new ClockSync(intTime);
        Thread serviceThread = new Thread(service);
        serviceThread.setDaemon(true);
        serviceThread.start();

        ClockSyncDemo demo = new ClockSyncDemo(service, intTime);
        boolean isDaemon = (args.length < 2);

        try {
            TCPIPConnectionManager tcpip =
                TCPIPConnectionManager.getConnectionManager();
            BluetoothConnectionManager bt =
                BluetoothConnectionManager.getConnectionManager();

            /* Start up routing service */
            SimplifiedFloodRouting routingService = new SimplifiedFloodRouting();
            routingService.addRouteChangeListener(demo);
            Thread routeThread = new Thread(routingService);
            routeThread.setDaemon(true);
            routeThread.start();

            /* Make connection */
            Iterator<String> iter;

            if (opts.listenTCPIP) {
                tcpip.setListenEnabled(true);
            }
            iter = opts.hostsTCPIP.iterator();
            while (iter.hasNext()) {
                tcpip.connectHost(iter.next());
            }

            if (opts.listenBT) {
                bt.setListenEnabled(true);
                System.out.println ("Listening on " +
                                    LocalDevice.getLocalDevice().getBluetoothAddress());
            }
            iter = opts.hostsBT.iterator();
            while (iter.hasNext()) {
                bt.connectDevice(iter.next());
            }

        } catch (IOException e) {
            System.err.printf("Failed to open connections: %1$s\n",
                              e.getMessage());
            System.exit(1);
        }

        demo.setVisible(true);
    }

    /* FIXME show this on the GUI somehow */
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
    /* FIXME show this on the GUI somehow */
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

    public void updateDisplay() {
        long tNet = networkTime.currentTimeMillis();
        long tInt = internalTime.currentTimeMillis();
        long diff = tNet - tInt;

        DateFormat f = DateFormat.getDateTimeInstance(DateFormat.DEFAULT,
                                                      DateFormat.DEFAULT,
                                                      Locale.ROOT);

        netTimeLabel.setText("Network: " + f.format(new Date(tNet)));
        intlTimeLabel.setText("Internal: " + f.format(new Date(tInt)));
        offsetLabel.setText("Offset (ms): " + Long.toString(diff));
    }

    public ClockSyncDemo(TimeProvider netTime,
                         TimeProvider intTime) {
        super("Clock sync demo");
        networkTime = netTime;
        internalTime = intTime;

        setDefaultCloseOperation(EXIT_ON_CLOSE);

        /* Assemble the GUI */
        offsetLabel = new JLabel();
        netTimeLabel = new JLabel();
        intlTimeLabel = new JLabel();

        Container contentPane = getContentPane();
        contentPane.setLayout(new BoxLayout(contentPane,
                                            BoxLayout.Y_AXIS));
        contentPane.add(intlTimeLabel);
        contentPane.add(netTimeLabel);
        contentPane.add(offsetLabel);

        /* Need a timer to periodically update the GUI */
        Timer updateTimer =
            new Timer(100, new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        updateDisplay();
                    }
                });
        updateTimer.setRepeats(true);
        updateTimer.start();

        updateDisplay();

        pack();

        /* Register that we want to receive connection change events */
        SystemBus.getSystemBus().addConnectionChangeListener(this);
    }
}

class ClockSyncOptions {

    boolean listenBT;
    boolean listenTCPIP;
    List<String> hostsTCPIP;
    List<String> hostsBT;
    long initOffset;

    public ClockSyncOptions(String[] args) {
        listenBT = false;
        listenTCPIP = false;
        hostsTCPIP = new LinkedList<String>();
        hostsBT = new LinkedList<String>();
        initOffset = 0;
        parseOptions(args);
    }

    private void parseOptions(String[] args) {
        Getopt g = new Getopt("clocksync-demo", args, "hBTb:t:o:");
        g.setOpterr(false); /* We'll print our own errors */
        int c;
        String arg;

        while ((c = g.getopt()) != -1) {
            switch (c) {
            case 'h':
                usage(0);
                break;

            case 'B':
                listenBT = true;
                break;
            case 'T':
                listenTCPIP = true;
                break;

            case 'b':
                hostsBT.add(g.getOptarg());
                break;
            case 't':
                hostsTCPIP.add(g.getOptarg());
                break;

            case 'o':
                try {
                    initOffset = (long) (Double.parseDouble(g.getOptarg()) * 1000);
                } catch (NumberFormatException e) {
                    System.err.println("Malformed offset " +
                                       g.getOptarg());
                    usage(1);
                }
                break;

            case ':':
                System.err.println ("You need an argument for the -"
                                    + (char) g.getOptopt() +
                                    " option.");
                usage(1);
                break;
            case '?':
                System.err.println ("Unrecognised option -"
                                    + (char) g.getOptopt());
                usage(1);
                break;
            }
        }
    }

    private void usage(int exitstatus) {
        System.out.println("Usage: \n"
                           + "        clocksync-demo [options]\n"
                           + "Options:\n"
                           + "        -h        Display help\n"
                           + "        -T        Accept TCP/IP connections\n"
                           + "        -B        Accept Bluetooth connections\n"
                           + "        -b addr   Connect to Bluetooth device\n"
                           + "        -t host   Connect to TCP/IP host\n"
                           + "        -o offset Initial clock offset (secs)\n");
        System.exit(exitstatus);
    }
}
