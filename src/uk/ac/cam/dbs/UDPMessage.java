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

/** <p>A message sent over UDP. A message consists of a payload, with
 * source and destination ports.</p>
 *
 * <p>Instances of this class are immutable: this is to ensure that they
 * can be passed between services and stored with minimum risk of them
 * being altered unexpectedly.</p>
 *
 * @see SystemBus
 * @see UDPMessageListener
 */
public class UDPMessage {
    /** Port this message should be delivered to. */
    private int to;
    /** Port this message was sent from. */
    private int from;
    /** Payload data */
    private byte[] payload;

    /** Create a new <code>UDPMessage</code>.
     *
     * @param toPort   Port number the message should be delivered to.
     * @param fromPort The port number the message was sent from.
     * @param payload  The contents of the message.
     **/
    public UDPMessage (int toPort, int fromPort, byte[] payload) {

        to = toPort;
        from = fromPort;
        this.payload = payload;

        int len = payload.length;

        if ((to >= 0x10000) || (to <= 0)) {
            throw new IllegalArgumentException("Invalid destination port.");
        }
        if ((from >= 0x10000) || from < 0) {
            throw new IllegalArgumentException("Invalid source port.");
        }
        if (len >= 0x10000) {
            throw new IllegalArgumentException("Payload too large.");
        }
    }

    /** Get the destination port.
     *
     * @return port number the message should be delivered to.
     **/
    public int getToPort() {
        return to;
    }

    /** Get the source port.
     *
     * @return The port number the message was sent from.
     */
    public int getFromPort() {
        return from;
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

        int to = getToPort();
        int from = getFromPort();
        int len   = getPayload().length;

        /** Turn values into signed 16-bit integers */
        to    = (to   >= 0x8000) ? (to   - 0x8000) : to;
        from  = (from >= 0x8000) ? (from - 0x8000) : from;
        len   = (len  >= 0x8000) ? (len  - 0x8000) : len;

        synchronized (out) {
            out.writeChar((char) from);
            out.writeChar((char) to);
            out.writeChar((char) len);
            out.writeChar(0); /* Don't bother with checksum */
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
    public static UDPMessage recv(DataInputStream in)
        throws IOException {

        int from, to;
        byte[] buf;
        synchronized (in) {
            int len, sum;
            /* Grab header */
            from = in.readChar();
            to = in.readChar();
            len = in.readChar();
            sum = in.readChar();

            /* Convert from signed char to unsigned integer */
            from = (from < 0) ? (from + 0x8000) : from;
            to =   (to   < 0) ? (to   + 0x8000) : to;
            len =  (len  < 0) ? (len  + 0x8000) : len;
            sum =  (sum  < 0) ? (sum  + 0x8000) : sum;

            /* Don't bother validating the checksum */

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
        return new UDPMessage(to, from, buf);
    }
}
