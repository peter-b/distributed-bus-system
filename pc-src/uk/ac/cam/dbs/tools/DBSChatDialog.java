package uk.ac.cam.dbs.tools;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.nio.charset.Charset;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import uk.ac.cam.dbs.bundle.*;

public class DBSChatDialog extends JDialog {

    private JTextField localEndpointField;
    private JTextField remoteEndpointField;
    private JTextField messageField;
    private JEditorPane logEditorPane;
    private JScrollPane logScrollPane;
    private JButton sendButton;

    private BundleAgent agent;
    private ReceiveHandler receive;

    protected static String STYLESHEET =
        "body { font-family: sans-serif; margin: 0px; padding: 0px; background-color: #ffffff; }\n" +
        "div.endpoint { font-size: x-small; color: blue; }\n" +
        "div.message { }\n" +
        "div.local { background-color: #c0c0c0 }\n" +
        "div.remote { }\n";

    public DBSChatDialog(JFrame frame, BundleAgent agent) {

        this.agent = agent;
        receive = this.new ReceiveHandler();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JLabel label;
        GridBagConstraints c = new GridBagConstraints();
        JPanel pane;

        /* Top panel containing local & remote endpoints */
        pane = new JPanel();
        pane.setLayout(new GridBagLayout());

        label = new JLabel("Local: ");
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.LINE_START;
        pane.add(label, c);

        localEndpointField = new JTextField();
        localEndpointField.setEditable(false);
        c.gridx = 1;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.LINE_END;
        pane.add(localEndpointField, c);

        label = new JLabel("Remote: ");
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.LINE_START;
        pane.add(label, c);

        remoteEndpointField = new JTextField();
        c.gridx = 1;
        c.gridy = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.LINE_END;
        pane.add(remoteEndpointField, c);

        getContentPane().add(pane, BorderLayout.NORTH);

        /* Centre panel containing log window */
        logEditorPane = new JEditorPane();
        logEditorPane.setContentType("text/html");
        logEditorPane.setText("<html><head><style type=\"text/css\">"
                              + STYLESHEET +
                              "</style></head><body/></html>");
        logEditorPane.setEditable(false);
        logScrollPane = new JScrollPane(logEditorPane);
        logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        logScrollPane.setPreferredSize(new Dimension(500, 300));
        logScrollPane.setMinimumSize(new Dimension(100, 50));
        getContentPane().add(logScrollPane, BorderLayout.CENTER);

        /* Message entry & "Send" button at the bottom. */
        ActionListener sendAction = new SendHandler();

        pane = new JPanel();
        pane.setLayout(new GridBagLayout());

        messageField = new JTextField();
        messageField.addActionListener(sendAction);
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.LINE_START;
        pane.add(messageField, c);

        sendButton = new JButton("Send");
        sendButton.addActionListener(sendAction);
        c.gridx = 1;
        c.gridy = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.LINE_END;
        pane.add(sendButton, c);

        getContentPane().add(pane, BorderLayout.SOUTH);

        pack();
    }

    public DBSChatDialog(JFrame frame, BundleAgent agent,
                         String localEndpoint, String remoteEndpoint) {
        this(frame, agent);
        setLocalEndpoint(localEndpoint);
        setRemoteEndpoint(remoteEndpoint);
    }

    public void setRemoteEndpoint(String endpoint) {
        remoteEndpointField.setText(endpoint);
    }

    public String getRemoteEndpoint() {
        return remoteEndpointField.getText();
    }

    public void setLocalEndpoint(String endpoint) {
        agent.unregisterEndpoint(getLocalEndpoint(), receive);
        localEndpointField.setText(endpoint);
        agent.registerEndpoint(endpoint, receive);
    }

    public String getLocalEndpoint() {
        return localEndpointField.getText();
    }

    private void updateLog(String htmlToAdd) {
        /* We must be in the event dispatch thread (EDT) */
        if (!SwingUtilities.isEventDispatchThread()) return;

        /* Add HTML to log */
        HTMLDocument d = (HTMLDocument) logEditorPane.getDocument();
        Element bodyElement = d.getElement(d.getDefaultRootElement(),
                                           StyleConstants.NameAttribute,
                                           HTML.Tag.BODY);
        try {
            d.insertBeforeEnd(bodyElement, htmlToAdd);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                                          e.getMessage(),
                                          "Unexpected error",
                                          JOptionPane.ERROR_MESSAGE);
        }

        /* Scroll log to bottom */
        JScrollBar vscroll = logScrollPane.getVerticalScrollBar();
        vscroll.setValue(vscroll.getMaximum());
    }

    private class ReceiveHandler
        implements EndpointEventListener, Runnable {

        private String htmlToAdd;
        private Object lock; /* Stops simultaneous access to htmlToAdd */

        public ReceiveHandler() {
            lock = new Object();
        }

        public void deliverBundle(Bundle b) {
            /* We're NOT in the Swing EDT here, so can't access
             * components. */

            /* Add message to HTML awaiting addition to log pane */
            String message = new String(b.getPayload(), Charset.forName("UTF-8"));
            String msgHtml = "<div class=\"remote\">"
                + "<div class=\"endpoint\">" + b.getSourceEndpoint() + "</div>"
                + "<div class=\"message\">" + message + "</div>"
                + "</div>";
            synchronized (lock) {
                if (htmlToAdd == null) {
                    htmlToAdd = msgHtml;
                } else {
                    htmlToAdd = htmlToAdd + msgHtml;
                }
            }

            /* Queue updating log pane on Swing EDT */
            SwingUtilities.invokeLater(this);
        }

        public void run() {
            /* We're in the Swing EDT here */
            synchronized (lock) {
                /* If there's nothing to do, return immediately! */
                if (htmlToAdd == null) return;

                updateLog(htmlToAdd);
                htmlToAdd = null;
            }
        }
    }

    private class SendHandler implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            /* We're in the Swing EDT here */

            /* Disable entry fields */
            messageField.setEnabled(false);
            sendButton.setEnabled(false);
            remoteEndpointField.setEnabled(false);

            /* Put bundle together & send it */
            String message = messageField.getText();
            Bundle b = new Bundle();
            b.setDestEndpoint(getRemoteEndpoint());
            b.setSourceEndpoint(getLocalEndpoint());
            b.setPayload(message.getBytes(Charset.forName("UTF-8")));
            b.setLifetime(60); /* FIXME widget for setting this? */
            agent.sendBundle(b);

            /* Add sent message to log */
            String htmlToAdd = "<div class=\"local\">"
                + "<div class=\"endpoint\">" + getLocalEndpoint() + "</div>"
                + "<div class=\"message\">" + message + "</div>"
                + "</div>";
            updateLog(htmlToAdd);

            /* Clear message entry field */
            messageField.setText("");

            /* Disable entry fields */
            messageField.setEnabled(true);
            sendButton.setEnabled(true);
            remoteEndpointField.setEnabled(true);
        }
    }

    private class WindowHandler implements WindowListener {
        public void windowOpened(WindowEvent e) { }

        public void windowClosing(WindowEvent e) {
            /* Window is closing and will be disposed of, so clean
             * up. */
            agent.unregisterEndpoint(getLocalEndpoint(), receive);
        }

        public void windowClosed(WindowEvent e) { }
        public void windowIconified(WindowEvent e) { }
        public void windowDeiconified(WindowEvent e) { }
        public void windowActivated(WindowEvent e) { }
        public void windowDeactivated(WindowEvent e) { }
    }
}
