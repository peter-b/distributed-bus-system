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

/** <p>Convenience class for working with SDNVs in byte
 * arrays. Self-Delimiting Numeric Values (SDNVs) are used extensively
 * in the Bundle Protocol as defined in RFC5050.</p>
 *
 * <p>Since SDNVs are arbitrary precision unsigned integers, it is
 * often useful to determine how many bytes a number will require when
 * converted to SDNV form, or, conversely, how long a SDNV stored in a
 * byte array is. The <code>getSdnvLength()</code> functions provide
 * this capability.</p>
 *
 * <p>To import these methods directly into a class:</p>
 * <pre>
 * import static uk.ac.cam.dbs.util.SdnvByteBufferHelper.*
 * </pre>
 */
public class SdnvByteBufferHelper {

    /** <p>Check the length of an encoded SDNV, in octets. Calculates
     * the length in octets of the SDNV stored at the offset
     * <code>off</code> in the byte array <code>buf</code>.</p>
     *
     * @param buf  Byte array to parse for an SDNV.
     * @param off  Offset within <code>buf</code> to start parsing.
     *
     * @return The length of the SDNV, in octets.
     */
    public static int getSdnvLength(byte[] buf, int off) {
        for (int i = 0; ; i++) {
            if ((buf[off+i] & 128) == 0) return i+1;
        }
    }

    /** <p>Check the size of a number once encoded as an SDNV.</p>
     *
     * @param value The value to calculate SDNV length for.
     *
     * @return The length in octets of the SDNV representing <code>value</code>.
     */
    public static int getSdnvLength(long value) {
        /* We don't allow -ve values */
        if (value < 0)
            throw new IllegalArgumentException("SDNVs may only encode positive integers");
        /* Each octet of an SDNV encodes 7 bits */
        int i;
        int result = 1;
        for (i = 9; i >= 0; i--) {
            int b = (int) (value >> (i*7)) & 127;
            if (b != 0) {
                result = i+1;
                break;
            }
        }
        return result;
    }

    /** <p>Decode an SDNV stored in a buffer.</p>
     *
     * @param buf  Byte array to parse for an SDNV.
     * @param off  Offset within <code>buf</code> to start parsing.
     *
     * @return The value of the SDNV.
     */
    public static long sdnvFromBytes(byte[] buf, int off) {
        long x = 0;
        boolean overflow = false;
        for (int i = 0; ; i++) {
            byte b = buf[i+off];
            x = (x << 7) + (b & 127);
            if ((b & 128) == 0) break;
            if (i >= 8) overflow = true;
        }
        if (overflow)
            throw new IllegalArgumentException("SDNVs of more than 9 octets are not supported");

        return x;
    }

    /** <p>Encode a value into a byte buffer as an SDNV.</p>
     *
     * @param value The value to encode.
     * @param buf   The byte buffer to store SDNV into.
     * @param off   The index into <code>buf</code> at which to start storing the SDNV.
     *
     * @return The length of the encoded SDNV.
     */
    public static int sdnvToBytes(long value, byte[] buf, int off) {
        if (value < 0)
            throw new IllegalArgumentException("SDNVs may only encode positive integers");

        boolean foundMsb = false;
        int k = off;
        for (byte i = 9; i >= 0; i--) {
            int b = (int) (value >> (i*7)) & 127;
            if ((b == 0) && !foundMsb && (i > 0)) continue;

            foundMsb = true;
            b |= (i > 0) ? 128 : 0;
            buf[k++] = (byte) b;
        }
        return k - off;
    }
}
