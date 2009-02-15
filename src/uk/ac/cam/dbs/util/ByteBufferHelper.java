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

package uk.ac.cam.dbs.util;

public class ByteBufferHelper {

    /** <p>Parses a signed integer from a byte array. Parses an
     * integer of <code>len*8</code> bytes from the byte array
     * <code>b</code>, with the MSB at <code>b[off]<code> and the LSB
     * at <code>b[off+len-1<code>. Assumes that the number is two's
     * complement encoded, and in network byte order.</p>
     *
     * @param b   Byte array to parse.
     * @param off Offset within <code>b</code> of MSB of integer.
     * @param len Length of integer in octets.
     *
     * @return The number retrieved.
     */
    public static long numFromBytes(byte[] b, int off, int len) {
        long result;

        /* First, pad the result with 1s or 0s depending on the MSB of
         * the stored number */
        if ((b[off] & 0x80) != 0) {
            result = ~(0L);
        } else {
            result = 0L;
        }

        /* Then shift in the data */
        for (int i = 0; i < len; i++) {
            result = (result << 8) | (0xff & b[off+i]);
        }

        return result;
    }


    /** <p>Writes a signed integer into a byte array. Writes an
     * integer of <code>len*8</code> bytes into the byte array
     * <code>b</code>, with the MSB at <code>b[off]<code> and the LSB
     * at <code>b[off+len-1<code>. The data is encoded in two's
     * complement format, and in network byte order.</p>
     *
     * @param b   Byte array to write to.
     * @param off Offset within <code>b</code> of MSB of integer.
     * @param len Length of integer in octets.
     */
    public static void numToBytes(long value, byte[] b, int off, int len) {
        /* Shift in the data */
        long shift = value;
        for (int i = len - 1; i >= 0; i--) {
            long v = shift & 0xff;
            if ((v & 0x80) > 0) {
                v -= 0x100;
            }
            b[off+i] = (byte) v;
            shift = shift >> 8;
        }
    }
}