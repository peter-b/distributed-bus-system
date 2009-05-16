package uk.ac.cam.dbs;

/** <p>Abstract class for DMP daemons.</p>
 *
 * <p>DMP daemons commonly need to implement a DMP message listener,
 * register it to receive incoming DMP messages, and start a thread to
 * carry out periodic actions.</p>
 *
 * <p>This class is designed to make implementing such a service
 * easier. Subclasses need only implement the <code>run()</code> and
 * <code>messageReceived()</code> methods. The service can then be
 * started and stopped as needed using the <code>start()</code> and
 * <code>stop()</code> methods, and the DMP port will automatically be
 * bound and unbound as needed.</p>
 *
 * <p>This class is designed only for simple applications. DMP
 * services that need multiple daemon threads, or which need to listen
 * on multiple DMP ports, should not rely on this class.</p>
 *
 * <p><b>Warning:</b> LeJOS users should be aware that LeJOS only
 * supports 256 threads total to be created during a program's
 * execution, and that starting and stopping services will rapidly use
 * up this limited thread pool.</p>
 *
 * @see DMPMessageListener
 * @see SystemBus
 */
public abstract class AbstractDMPDaemon {

    private boolean enabled;
    private Object enabledLock;
    private Thread daemonThread;
    private DMPMessageListener listener;

    /** DMP port used by the DMP daemon. */
    private int dmpPort;

    /** Create a new <code>AbstractDMPDaemon</code>. */
    public AbstractDMPDaemon() {
        enabled = false;
        enabledLock = new Object();
        daemonThread = null;
        listener = new DMPMessageListener() {
                public void recvDMPMessage(BusConnection conn, DMPMessage msg) {
                    AbstractDMPDaemon.this.messageReceived(conn, msg);
                };
            };
        dmpPort = 0;
    }

    /** Create a new <code>AbstractDMPDaemon</code>.
     *
     * @param port DMP port to listen on.
     */
    public AbstractDMPDaemon(int port) {
        this();
        dmpPort = port;
    }

    /** <p>Start the DMP daemon. If the DMP daemon is already running,
     * do nothing.</p>
     *
     * @see #run()
     * @see #isRunning()
     */
    public void start() throws DMPBindException {
        synchronized (enabledLock) {
            if (!enabled) {
                enabled = true;
                SystemBus.getSystemBus().addDMPService(listener, dmpPort);
            }
            if (!isRunning()) {
                daemonThread = new Thread() {
                        public void run() {
                            AbstractDMPDaemon.this.run();
                        }
                    };
                daemonThread.setDaemon(true);
                daemonThread.start();
            }
        }
    }

    /** <p>Stop the DMP daemon. If the DMP daemon is not running, do nothing.</p>
     *
     * <p>Note that this method does not guarantee that the daemon is
     * stopped when it returns. Whether or not the daemon stops in a
     * timely manner after the <code>stop()</code> method is called
     * depends on the subclass's implementation of
     * <code>run()</code>. The <code>isRunning()</code> method can be
     * used to check if the daemon has stopped.</p>
     *
     * @see #run()
     * @see #isRunning()
     */
    public void stop() {
        synchronized (enabledLock) {
            if (enabled) {
                enabled = false;
                SystemBus.getSystemBus().removeDMPService(listener, dmpPort);
            }
            if (isRunning()) {
                daemonThread.interrupt();
            }
        }
    }

    /** <p>Test if the daemon is running. Note that this does not test
     * if the daemon <em>should</em> be running: it tests if it is
     * <em>actually</em> running. This is intended to be used by
     * external code monitoring the state of the daemon.</p>
     *
     * @return <code>true</code> if the daemon is running, and
     *         <code>false</code> otherwise.
     *
     * @see #isRunning()
     */
    public final boolean isRunning() {
        synchronized (enabledLock) {
            return ((daemonThread != null) && (daemonThread.isAlive()));
        }
    }

    /** <p>Check if the daemon is enabled. Note that this does not
     * test if the daemon <em>is</em> running: it tests if it
     * <em>should be</em> running. This is intended to be used by
     * implementations of the <code>run()</code> method.</p>
     *
     * @see #isEnabled()
     * @see #run()
     */
    protected final boolean isEnabled() {
        return enabled;
    }

    /** Get the DMP port used by the daemon. */
    protected final int getDMPPort() {
        return dmpPort;
    }

    /** <p>Set the DMP port used by the daemon. If the daemon is
     * running, the current port is unbound, and the new port bound
     * without restarting the daemon.</p>
     *
     * @param port DMP port to listen on.
     *
     * @throws DMPBindException If <code>port</code> is already in use. If so,
     *                          the current port is unaltered.
     */
    protected final void setDMPPort(int port) throws DMPBindException {
        synchronized (enabledLock) {
            if (port == dmpPort) return;
            if (enabled) {
                SystemBus.getSystemBus().addDMPService(listener, port);
                SystemBus.getSystemBus().removeDMPService(listener, dmpPort);
            }
            dmpPort = port;
        }
    }

    /** <p>Run the daemon.</p>
     *
     * <p>Subclasses should override this method with their own
     * implementation. The implementation should check whether the
     * daemon is enabled on a regular basis by calling the
     * <code>isEnabled()</code> method, and clean up and stop if it
     * returns <code>false</code>. The thread will be interrupted if
     * the value changes.</p>
     *
     * <p>The default implementation returns immediately.</p>
     *
     * @see #isEnabled()
     */
    protected void run() {
    }

    /** <p>Handle an incoming DMP message.</p>
     *
     * <p>Subclasses should override this with the actions to be taken
     * when a message arrives on the daemon's bound DMP port.</p>
     *
     * <p>The default implementation returns immediately.</p>
     *
     * @param connection The connection over which the message was
     *                   received.
     * @param msg        The DMP message to be processed.
     *
     * @see DMPMessageListener#recvDMPMessage(BusConnection, DMPMessage)
     */
    protected void messageReceived(BusConnection connection, DMPMessage msg) {
    }
}
