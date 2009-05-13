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

package uk.ac.cam.dbs;

import java.io.InputStream;
import java.io.DataInputStream;
import java.io.OutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.EOFException;

/** <p>A message sent over DMP. A message consists of a payload, a
 * service port number, and a checksum.</p>
 *
 * <p>Instances of this class are immutable: this is to ensure that they
 * can be passed between services and stored with minimum risk of them
 * being altered unexpectedly.</p>
 *
 * <p><strong>Note:</strong> This implementation does not currently
 * generate or verify checksums.</p>
 *
 * @see SystemBus
 * @see DMPMessageListener
 */
public class DMPMessage {
    /** Service port number. */
    private int port;
    /** Payload data */
    private byte[] payload;

    /** Create a new <code>DMPMessage</code>.
     *
     * @param port The port number associated with the service
     *             responsible for the message.
     * @param payload  The contents of the message.
     **/
    public DMPMessage (int port, byte[] payload) {

        this.payload = payload;
        this.port = port;

        int len = payload.length;

        if ((port >= 0x10000) || (port <= 0)) {
            throw new IllegalArgumentException("Invalid port number.");
        }
        if (len >= 0x10000) {
            throw new IllegalArgumentException("Payload too large.");
        }
    }

    /** Get the service port number.
     *
     * @return port number associated with the service responsible for
     *         the message.
     **/
    public int getPort() {
        return port;
    }

    /** Get the payload data.
     *
     * @return the contents of the message. */
    public byte[] getPayload() {
        return payload;
    }

    /** Get the full size of the message including headers.
     *
     * @return size in octets.
     */
    public int getSize() {
        return payload.length + 8;
    }

    /** Send this message over a stream.
     *
     * @param out the <code>DataOutputStream</code> the message should
     *            be serialised onto.
     *
     * @throws IOException if an error occurs in transmission.
     */
    public void send(DataOutputStream out) throws IOException {

        int port = getPort();
        int len   = getPayload().length;

        /** Turn values into signed 16-bit integers */
        port  = (port >= 0x8000) ? (port - 0x10000) : port;
        len   = (len  >= 0x8000) ? (len  - 0x10000) : len;

        synchronized (out) {
            out.writeChar((char) port);
            out.writeChar((char) len);
            out.writeChar(0); /* FIXME Don't bother with checksum */
            out.write(getPayload(), 0, len);
            out.flush();
        }
    }

    /** Scan a message from a stream.
     *
     * @param in the <code>DataInputStream</code> the message should
     *           be read from.
     *
     * @throws IOException if an error occurs in reception.
     */
    public static DMPMessage recv(DataInputStream in)
        throws IOException {

        int port;
        byte[] buf;
        synchronized (in) {
            int len, sum;
            /* Grab header */
            port = in.readChar();
            len = in.readChar();
            sum = in.readChar();

            /* Convert from signed char to unsigned integer */
            port = (port < 0) ? (port + 0x10000) : port;
            len =  (len  < 0) ? (len  + 0x10000) : len;
            sum =  (sum  < 0) ? (sum  + 0x10000) : sum;

            /* FIXME: Don't bother validating the checksum */

            /* Grab payload */
            buf = new byte[len];
            int read_len = 0;
            while (read_len < len) {
                int status = in.read(buf, read_len,
                                     (len - read_len));
                if (status < 0) {
                    throw new EOFException("Unexpected end of file reading message payload.");
                }
                read_len += status;
            }
        }
        return new DMPMessage(port, buf);
    }
}
