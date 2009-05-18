package uk.ac.cam.dbs.examples;

import uk.ac.cam.dbs.*;
import uk.ac.cam.dbs.bundle.*;
import uk.ac.cam.dbs.sfrp.SimplifiedFloodRouting;
import lejos.nxt.ButtonListener;
import lejos.nxt.Button;
import java.util.Vector;

public class ButtonMonitor
    implements ButtonListener, EndpointEventListener {

    private static byte[] SUBSCRIBE_WORD;
    private static byte[] UNSUBSCRIBE_WORD;
    private static byte[] LEFT_WORD;
    private static byte[] RIGHT_WORD;
    static {
        SUBSCRIBE_WORD = new byte[] {'s', 'u', 'b', 's', 'c', 'r', 'i', 'b', 'e'};
        UNSUBSCRIBE_WORD = new byte[] {'u', 'n', 's', 'u', 'b', 's', 'c', 'r', 'i', 'b', 'e'};
        LEFT_WORD = new byte[] {'L', 'E', 'F', 'T'};
        RIGHT_WORD = new byte[] {'R', 'I', 'G', 'H', 'T'};
    }

    private static int LIFETIME = 10; /* Seconds */

    private Vector listeners; /* of String */
    private BundleAgent agent;
    private String localEndpoint;

    public ButtonMonitor(BundleAgent agent) {
        this.agent = agent;
        listeners = new Vector();
        localEndpoint = "dtn://[" +
            SystemBus.getSystemBus().getMainAddress().toString() +
            "]/buttonmonitor";
        agent.registerEndpoint(localEndpoint, this);

        Button.LEFT.addButtonListener(this);
        Button.RIGHT.addButtonListener(this);
    }

    public static void main(String[] args) {
        ClockSync networkTime = new ClockSync(TimeProvider.systemTimeProvider());
        SimplifiedFloodRouting routing = new SimplifiedFloodRouting();
        BundleAgent agent = new BundleAgent();
        agent.setRoutingProvider(routing);

        try {
            BluetoothConnectionManager bt = BluetoothConnectionManager.getConnectionManager();
            bt.setListenEnabled(true);
            SystemBus.getSystemBus().setMainAddress(bt.getLocalAddress());

            Thread clockThread = new Thread(networkTime);
            clockThread.setDaemon(true);
            clockThread.start();

            routing.start();
            agent.start();
        } catch (Exception e) {
            System.out.println("Startup failed: \n" + e.getMessage());
        }

        ButtonMonitor monitor = new ButtonMonitor(agent);

        System.out.println("ButtonMonitor started");

        /* Go to sleep. For ever. */
        while (true) {
            try {
                Thread.sleep(1000);
                //              System.gc();
            } catch (InterruptedException e) {
                /* Tum-ti-tum */
            }
        }
    }

    public void deliverBundle(Bundle bdl) {
        /* First check if this is either "subscribe" or "unsubscribe". */
        boolean subscribe = true;
        byte[] payload = bdl.getPayload();

        if ((payload.length != SUBSCRIBE_WORD.length) &&
            (payload.length != UNSUBSCRIBE_WORD.length)) {
            return;
        }

        switch (payload[0]) {
        case 's':
            subscribe = true;
            for (int i = 0; i < SUBSCRIBE_WORD.length; i++) {
                if (payload[i] != SUBSCRIBE_WORD[i]) return;
            }
            break;
        case 'u':
            subscribe = false;
            for (int i = 0; i < UNSUBSCRIBE_WORD.length; i++) {
                if (payload[i] != UNSUBSCRIBE_WORD[i]) return;
            }
            break;
        default:
            return;
        }

        String tgt = bdl.getSourceEndpoint();

        /* Send a reply */
        bdl.setDestEndpoint(bdl.getSourceEndpoint());
        bdl.setSourceEndpoint(localEndpoint);
        agent.sendBundle(bdl);

        synchronized (listeners) {
            for (int i = 0; i < listeners.size(); i++) {
                String e = (String) listeners.elementAt(i);
                if (e.equals(tgt)) {
                    /* If we're supposed to be unsubscribing the
                    sender, remove them. Otherwise, the sender is
                    already subscribed, so do nothing. */
                    if (!subscribe) listeners.removeElementAt(i);
                    return;
                }
            }
            listeners.addElement(tgt);
        }

        if (subscribe) {
            System.out.println("Received subscribe");
        } else {
            System.out.println("Received unsubscribe");
        }
    }

    public void buttonPressed(Button b) {
        /* Only send out a message when the left or right buttons get
         * pressed down. */
        byte[] payload;
        if (b == Button.LEFT) {
            payload = LEFT_WORD;
        } else if (b == Button.RIGHT) {
            payload = RIGHT_WORD;
        } else {
            return;
        }

        /* Due to the way the BundleAgent works at the moment, we need
         * to make new Bundle objects for each subscribed endpoint. In
         * the future, when the BundleAgent supports multicast, this
         * will be much easier. Yes.  */
        synchronized (listeners) {
            for (int i = 0; i < listeners.size(); i++) {
                Bundle bdl = new Bundle();
                bdl.setLifetime(LIFETIME);
                bdl.setPayload(payload);
                bdl.setSourceEndpoint(localEndpoint);
                bdl.setDestEndpoint((String) listeners.elementAt(i));
                agent.sendBundle(bdl);
            }
        }

        System.out.println("Button pressed");
    }

    public void buttonReleased(Button b) {
    }

}
