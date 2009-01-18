package uk.ac.cam.dbs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** A connection to another device.
 */
public interface BusConnection {

    /** Get the local address of the connection. Used to identify a
     * connection by remote devices.
     *
     * @return The local interface address.
     */
    byte[] getLocalAddress();

    /** Tests if this <code>BusConnection</code> is connected.
     *
     * @return <code>true</code> if the connection is active.
     */
    boolean isConnected();

    /** Disconnects the connection.
     *
     * @throws <code>IOException</code> if an error occurs during
     *         disconnection.
     */
    void disconnect() throws IOException;

    /** <p>Gets an <code>InputStream</code> for the connection.</p>
     *
     * <p>Implementations should ensure that repeated calls to this
     * function return an equivalent stream. If different streams are
     * returned, they must be thread-safe.</p>
     *
     * @return a <code>InputStream</code> for receiving data from the
     *         remote device.
     */
    InputStream getInputStream() throws IOException;

    /** <p>Gets an <code>OutputStream</code> for the connection.</p>
     *
     * <p>Implementations should ensure that repeated calls to this
     * function return an equivalent stream. If different streams are
     * returned, they must be thread-safe.</p>
     *
     * @return a <code>OutputStream</code> for sending data to the
     *         remote device.
     */
    OutputStream getOutputStream() throws IOException;
}
