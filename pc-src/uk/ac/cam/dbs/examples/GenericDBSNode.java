package uk.ac.cam.dbs.examples;

import uk.ac.cam.dbs.*;
import uk.ac.cam.dbs.bundle.*;
import uk.ac.cam.dbs.sfrp.*;

import gnu.getopt.Getopt;

public class GenericDBSNode {

    private BundleAgent bundleDaemon;
    private SimplifiedFloodRouting sfrpDaemon;
    private ClockSync clockDaemon;

    private static boolean verbose;

    public GenericDBSNode() {
        bundleDaemon = new BundleAgent();
        sfrpDaemon = new SimplifiedFloodRouting();
        clockDaemon = new ClockSync();

        bundleDaemon.setRoutingProvider(sfrpDaemon);
        bundleDaemon.setTimeProvider(clockDaemon);

        SystemBus.getSystemBus().addConnectionChangeListener(new BusConnectionChangeListener() {
                public void connectionChanged(BusConnection connection, int status) {
                    if (!verbose) return;
                    String message = "# Link to [" +
                        connection.getRemoteAddress().toString() + "] ";
                    if (status == CONNECTION_ADDED) {
                        message = message + "UP";
                    } else if (status == CONNECTION_REMOVED) {
                        message = message + "DOWN";
                    } else {
                        message = message + "CHANGED";
                    }
                    System.out.println (message);
                }
            });
        sfrpDaemon.addRouteChangeListener(new SfrpRouteChangeListener() {
                public void routeChanged (InterfaceAddress addr, int status) {
                    if (!verbose) return;
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
            });
    }

    public void start() {
        Thread clockThread = new Thread(clockDaemon);
        clockThread.start();
        try {
            sfrpDaemon.start();
            bundleDaemon.start();
        } catch (Exception e) {
            throw new IllegalStateException("Could not start node: " + e.getMessage());
        }
    }

    public static void main(String[] args) {

        GenericDBSNode node = new GenericDBSNode();

        Getopt g = new Getopt("dbsnode", args, "hva:Tt:p:Bb:");
        g.setOpterr(false); /* We'll print our own errors. */
        int c;
        String arg;

        while ((c = g.getopt()) != -1) {
            switch (c) {
            case 'h':
                usage(0);
                break;

            case 'a':
                try {
                    InterfaceAddress addr = new InterfaceAddress(g.getOptarg());
                    SystemBus.getSystemBus().setMainAddress(addr);
                } catch (NumberFormatException e) {
                    System.err.println("Malformed address: " + g.getOptarg());
                    usage(1);
                }
                break;

            case 'B':
                try {
                    BluetoothConnectionManager.getConnectionManager().setListenEnabled(true);
                } catch (Exception e) {
                    System.err.println("Cannot start Bluetooth: " + e.getMessage());
                }
                break;
            case 'T':
                TCPIPConnectionManager.getConnectionManager().setListenEnabled(true);
                break;

            case 'p':
                try {
                    int port = Integer.parseInt(g.getOptarg());
                    if ((port > 65535) || (port < 1)) {
                        throw new IllegalArgumentException("Port number out of range: "
                                                           + g.getOptarg());
                    }
                    TCPIPConnectionManager.getConnectionManager().setTCPPort(port);
                } catch (Exception e) {
                    System.err.println("Malformed TCP port number: " + e.getMessage());
                    usage(1);
                }
                break;

            case 't':
                try {
                    String tcpHost = g.getOptarg();
                    if (tcpHost.indexOf(":") != -1) {
                        String[] parts = tcpHost.split(":", 2);
                        int port = Integer.parseInt(parts[1]);
                        TCPIPConnectionManager.getConnectionManager().connectHost(parts[0], port);
                    } else {
                        TCPIPConnectionManager.getConnectionManager().connectHost(tcpHost);
                    }
                } catch (Exception e) {
                    System.err.println("Could not establish TCP connection: "
                                       + e.getMessage());
                }
                break;

            case 'b':
                try {
                    BluetoothConnectionManager.getConnectionManager().connectDevice(g.getOptarg());
                } catch (Exception e) {
                    System.err.println("Could not establish Bluetooth connection: "
                                       + e.getMessage());
                }
                break;

            case 'v':
                verbose = true;
                break;

            case ':':
                System.err.println("You need an argument for the -" + (char) g.getOptopt() +
                                   " option.");
                usage(1);
                break;
            case '?':
                System.err.println("Unrecognised option -" + (char) g.getOptopt());
                usage(1);
                break;
            }
        }

        node.start();
    }

    private static void usage(int exitstatus) {
        System.out.println("Usage: \n"
                           + "        GenericDBSNode [options]\n\n"
                           + "Options:\n"
                           + "        -h        Display help\n"
                           + "        -T        Accept TCP/IP connections\n"
                           + "        -p port   TCP daemon port\n"
                           + "        -t host   Connect to TCP/IP host\n"
                           + "        -B        Accept Bluetooth connections\n"
                           + "        -b addr   Connect to Bluetooth device\n"
                           + "        -a addr   Main system bus address\n"
                           + "        -v        Print extra diagnostic info\t");
        System.exit(exitstatus);
    }
}
