package uk.ac.cam.dbs;

/** Signals that an error occurred while attempting to bind a service
 * to to a local UDP port.
 */
public class UDPBindException extends java.io.IOException {

    /** Construct a new <code>UDPBindException</code> with no detailed
     * message. */
    public UDPBindException() {
        super();
    }

    /** Constructs a new <code>UDPBindException</code> with the
     * specified detail message as to why the bind error occurred. */
    public UDPBindException(String message) {
        super(message);
    }
}
