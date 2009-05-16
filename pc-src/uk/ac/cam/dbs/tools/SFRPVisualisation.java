package uk.ac.cam.dbs.tools;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Vector;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import uk.ac.cam.dbs.InterfaceAddress;
import uk.ac.cam.dbs.sfrp.*;

/** <p>Swing widget for displaying SFRP routability.</p>
 *
 * <p>Displays a ring of filled circles, one for each Simplified Flood
 * Routing Protocol routable host on the network, along with a circle
 * in the middle representing the local host. The bus system addresses
 * of each remote host are displayed beneath their respective
 * circles.</p>
 *
 * <p>When a host connects or the next hop towards it changes, it is
 * highlighted in red, and when a host becomes unroutable and is about
 * to be removed, it is faded to grey.</p>
 *
 * @see uk.ac.cam.dbs.sfrp.SimplifiedFloodRouting
 */
public class SFRPVisualisation extends JPanel {

    private List<HostRecord> knownHosts;
    private boolean awaitingRepaint;

    private static final int DOT_RADIUS = 25;
    private static final int FLASH_TIME = 1000;

    private Color activeColor, alertColor, inactiveColor;

    /** Create a new <code>SFRPVisualisation</code>. Routability
     * information will be drawn from the SFRP daemon <code>routing</code>
     *
     * @param routing   SFRP daemon to draw routing data from.
     */
    public SFRPVisualisation(SimplifiedFloodRouting routing) {
        knownHosts = new LinkedList<HostRecord>();

        /* Do initial population of hosts */
        synchronized (knownHosts) {
            routing.addRouteChangeListener(this.new ChangeHandler());

            Vector<InterfaceAddress> addresses =
                (Vector<InterfaceAddress>) routing.getKnownRoutes();

            Iterator<InterfaceAddress> iter = addresses.iterator();
            while (iter.hasNext()) {
                HostRecord rec = new HostRecord(iter.next());
                rec.lastUpdate = 0; /* Don't use alert color at all. */
                knownHosts.add(rec);
            }
        }

        /* Set up colors */
        activeColor = new Color(0, 153, 255); /* Blue 7 in OpenOffice.org default palette */
        alertColor = Color.RED;
        inactiveColor = new Color(153, 153, 153); /* Gray 4 in OpenOffice.org default palette */
    }

    /** {@inheritDoc}
     */
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        synchronized (knownHosts) {
            awaitingRepaint = false;

            int N = knownHosts.size();
            long now = System.currentTimeMillis();

            /* Work out if we have enough space */
            int minringradius = (int) ((2*DOT_RADIUS*N)/(Math.PI));
            int widthNeeded = 2*(minringradius + DOT_RADIUS);

            if ((widthNeeded > getHeight()) || (widthNeeded > getWidth())) {
                g.drawString("Too small to show hosts.", 10, 20);
                return;
            }

            Color foreground = g.getColor();

            /* Do some calculations */
            int availableSize = Math.min(getWidth(), getHeight()) - 2*DOT_RADIUS;
            int centreX = getWidth()/2;
            int centreY = getHeight()/2;
            int ringRadius = (availableSize - 2*DOT_RADIUS)/2;
            double phi = 2*Math.PI/N; /* Angle for each dot. */

            /* Draw circles on the first pass. */
            Iterator<HostRecord> iter = knownHosts.iterator();
            int n = 0;
            while (iter.hasNext()) {
                HostRecord rec = iter.next();

                int dotX = centreX + (int) (ringRadius*Math.sin(n*phi + Math.PI/2));
                int dotY = centreY + (int) (ringRadius*Math.cos(n*phi + Math.PI/2));

                n++;

                /* Draw lines connecting to the centre */
                g.setColor(Color.BLACK);
                g.drawLine(dotX, dotY, centreX, centreY);

                /* Choose what colour to draw fill circle:
                 *  - Blue normally.
                 *  - Red if recently added or changed.
                 *  - Grey if recently disconnected.
                 */
                if (!rec.active) {
                    g.setColor(inactiveColor);
                } else if (FLASH_TIME > now - rec.lastUpdate) {
                    g.setColor(alertColor);
                } else {
                    g.setColor(activeColor);
                }

                /* Draw filled circle, then outline. */
                g.fillOval(dotX-DOT_RADIUS, dotY-DOT_RADIUS,
                           2*DOT_RADIUS, 2*DOT_RADIUS);
                g.setColor(Color.BLACK);
                g.drawOval(dotX-DOT_RADIUS, dotY-DOT_RADIUS,
                           2*DOT_RADIUS, 2*DOT_RADIUS);

                /* Store circle bounds */
                rec.circleBounds = new Rectangle(dotX-DOT_RADIUS, dotY-DOT_RADIUS,
                                             2*DOT_RADIUS, 2*DOT_RADIUS);
            }

            /* Draw a circle in the middle to represent the local node. */
            g.setColor(activeColor);
            g.fillOval(centreX-DOT_RADIUS, centreY-DOT_RADIUS,
                       2*DOT_RADIUS, 2*DOT_RADIUS);
            g.setColor(Color.BLACK);
            g.drawOval(centreX-DOT_RADIUS, centreY-DOT_RADIUS,
                       2*DOT_RADIUS, 2*DOT_RADIUS);

            /* On the second pass, draw address strings. */
            FontMetrics metrics = g.getFontMetrics();

            iter = knownHosts.iterator();
            n = 0;
            while (iter.hasNext()) {
                HostRecord rec = iter.next();

                int dotX = centreX + (int) (ringRadius*Math.sin(n*phi + Math.PI/2));
                int dotY = centreY + (int) (ringRadius*Math.cos(n*phi + Math.PI/2));

                n++;

                String addrString = rec.address.toString();
                Rectangle2D stringBounds = metrics.getStringBounds(addrString, g);
                int strX = (int) (dotX - stringBounds.getWidth()/2);
                int strY = (int) (dotY + stringBounds.getHeight() + DOT_RADIUS);

                g.drawString(addrString, strX, strY);
                rec.addrBounds =
                    new Rectangle(strX,
                                  (int) (strY - stringBounds.getHeight()),
                                  (int) stringBounds.getWidth(),
                                  (int) stringBounds.getHeight());
            }
        }
    }

    public Dimension getPreferredSize() {
        int widthNeeded;
        synchronized (knownHosts) {
            int minringradius = (int) ((2*DOT_RADIUS*knownHosts.size())/(Math.PI));
            widthNeeded = 2*(minringradius + DOT_RADIUS);
        }
        return new Dimension(widthNeeded, widthNeeded);
    }

    /** <p>Get the address of the host which has its graphics
     * intersected by a given point.</p>
     *
     * <p>If <code>location</code> is within the bounds of the circle
     * or text for a host, returns that host's address. Useful for
     * implementing listeners for mouse events on the
     * visualisation.</p>
     *
     * @param location  Point to test for intersection with host circles.
     *
     * @returns the address of the host intersected on the display, or
     *          <code>null</code>.
     */
    public InterfaceAddress getHostAtPoint(Point location) {
        synchronized (knownHosts) {
            Iterator<HostRecord> iter = knownHosts.iterator();
            while (iter.hasNext()) {
                HostRecord rec = iter.next();
                if (((rec.circleBounds != null) && rec.circleBounds.contains(location))
                    || ((rec.addrBounds != null) && rec.addrBounds.contains(location))) {
                    return rec.address;
                }
            }
        }
        return null;
    }

    private class HostRecord {
        private InterfaceAddress address;
        private boolean active;
        private long lastUpdate;
        Rectangle circleBounds, addrBounds;

        HostRecord(InterfaceAddress address) {
            this.address = address;
            active = true;
            lastUpdate = System.currentTimeMillis();
            circleBounds = null;
            addrBounds = null;
        }
    }

    private class ChangeHandler
        implements SfrpRouteChangeListener, Runnable, ActionListener {

        public void routeChanged (InterfaceAddress addr, int status) {

            synchronized (knownHosts) {
                /* First look for the record pertaining to this address */
                Iterator<HostRecord> iter = knownHosts.iterator();
                HostRecord rec = null;
                boolean found = false;
                while (iter.hasNext()) {
                    rec = iter.next();
                    if (rec.address.equals(addr)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    rec = new HostRecord(addr);
                    knownHosts.add(rec);
                }

                if (status == ROUTE_ADDED) {
                    rec.active = true;
                } else if (status == ROUTE_REMOVED) {
                    rec.active = false;
                }

                rec.lastUpdate = System.currentTimeMillis();
            }

            /* We also need to do some stuff in the GUI thread, so
             * schedule that! */
            SwingUtilities.invokeLater(this);

        }

        public void run() {
            /* First, schedule a repaint if we haven't already done so
             * recently. */
            repaint();
            /* Secondly, schedule a cleanup of any finished-with
             * address records. */
            new Timer(FLASH_TIME * 3/2, this).start();
        }

        public void actionPerformed(ActionEvent e) {
            boolean changesMade = false;
            synchronized (knownHosts) {
                long now = System.currentTimeMillis();

                /* Clean up any address records that are dead. */
                ListIterator<HostRecord> iter = knownHosts.listIterator();

                while (iter.hasNext()) {
                    HostRecord rec = iter.next();
                    if (!rec.active && (rec.lastUpdate + FLASH_TIME < now)) {
                        iter.remove();
                        changesMade = true;
                    }
                }
                repaint();
            }
        }
    }
}
