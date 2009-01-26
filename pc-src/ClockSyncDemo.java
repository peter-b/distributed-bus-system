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

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.Timer;

public class ClockSyncDemo extends JFrame {

    TimeProvider networkTime;
    TimeProvider internalTime;

    JLabel offsetLabel;
    JLabel netTimeLabel;
    JLabel intlTimeLabel;

    static final int NUM_THREADS = 3;
    static final int RUN_TIME = 30000; /* ms */

    public static void main(String[] args) {

        if (args.length < 1) {
            System.err.println("You must specify an initial clock offset in seconds!");
            System.exit(1);
        }

        final long initOffset = Long.parseLong(args[0]) * 1000;

        TimeProvider intTime = new TimeProvider() {
                public long currentTimeMillis() {
                    return System.currentTimeMillis() + initOffset;
                }
            };

        ClockSync service = new ClockSync(intTime);
        Thread serviceThread = new Thread(service);
        serviceThread.setDaemon(true);
        serviceThread.start();

        ClockSyncDemo demo = new ClockSyncDemo(service, intTime);
        boolean isDaemon = (args.length < 2);

        /* Make connection */
        try {
            TCPIPConnectionManager tcpip =
                TCPIPConnectionManager.getConnectionManager();
            if (!isDaemon) {
                System.out.println("# Connecting...");
                tcpip.connectHost(args[1]);
            } else {
                System.out.println("# Listening for connections...");
                tcpip.setListenEnabled(true);
            }
        } catch (IOException e) {
            System.err.printf("Failed to open connection: %1$s\n",
                              e.getMessage());
            System.exit(1);
        }

        demo.setVisible(true);
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
    }
}
