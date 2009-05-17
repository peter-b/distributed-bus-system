package uk.ac.cam.dbs.tools;

import uk.ac.cam.dbs.sfrp.*;
import uk.ac.cam.dbs.bundle.*;
import uk.ac.cam.dbs.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseListener;
import java.awt.event.MouseEvent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class DBSChat extends JFrame {

    private SimplifiedFloodRouting routing;
    private BundleAgent agent;

    private SFRPVisualisation sfrpVis;

    private int chatCount;

    public static void main(String[] args) {

        /* FIXME replace this with GUI for setting up connections */
        try {
            TCPIPConnectionManager tcpip =
                TCPIPConnectionManager.getConnectionManager();
            if (args[0].indexOf(":") != -1) {
                String[] parts = args[0].split(":", 2);
                tcpip.connectHost(parts[0], Integer.parseInt(parts[1]));
            } else {
                tcpip.connectHost(args[0]);
            }

        } catch (Exception e) {
            System.err.println("Could not connect: " + e.getMessage());
            System.exit(1);
        }


        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    SimplifiedFloodRouting routing = new SimplifiedFloodRouting();
                    BundleAgent agent = new BundleAgent();
                    agent.setRoutingProvider(routing);

                    try {
                        routing.start();
                        agent.start();
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(null,
                                                      e.getMessage(),
                                                      "Unexpected error",
                                                      JOptionPane.ERROR_MESSAGE);
                        System.exit(1);
                    }

                    DBSChat app = new DBSChat(agent, routing);
                    app.setVisible(true);
                    //app.useArguments(args);
                    app.setDefaultCloseOperation(EXIT_ON_CLOSE);

                }
            });
    }

    public DBSChat(BundleAgent agent,
                   SimplifiedFloodRouting routing) {
        super();

        this.agent = agent;
        this.routing = routing;
        chatCount = 0;

        /* Default layout is BorderLayout, so don't need to set it
         * explicitly. */
        //setLayout(new BorderLayout());

        sfrpVis = new SFRPVisualisation(routing);
        sfrpVis.addMouseListener(this.new SFRPMouseHandler());

        getContentPane().add(sfrpVis, BorderLayout.CENTER);

        setPreferredSize(new Dimension(400, 400));
        setTitle("DBSChat " + SystemBus.getSystemBus().getMainAddress().toString());

        pack();
    }

    private String nextChatEndpoint() {
        return endpointFromAddress(SystemBus.getSystemBus().getMainAddress())
            + "dbschat/" + Integer.toString(++chatCount);
    }

    private static String endpointFromAddress(InterfaceAddress a) {
        return "dtn://[" + a.toString() + "]/";
    }

    private class SFRPMouseHandler implements MouseListener {
        public void mouseClicked(MouseEvent e) {
            /* If a user double-clicks on a node, launch a chat
             * dialog */
            if (e.getClickCount() < 2) return;

            InterfaceAddress tgt = sfrpVis.getHostAtPoint(e.getPoint());
            if (tgt == null) return;

            DBSChatDialog dialog = new DBSChatDialog(DBSChat.this, agent,
                                                     nextChatEndpoint(),
                                                     endpointFromAddress(tgt));
            dialog.setTitle("DBSChat Session " + Integer.toString(chatCount));
            dialog.setVisible(true);
        }
        public void mouseEntered(MouseEvent e) { }
        public void mouseExited(MouseEvent e) { }
        public void mousePressed(MouseEvent e) { }
        public void mouseReleased(MouseEvent e) { }
    }
}
