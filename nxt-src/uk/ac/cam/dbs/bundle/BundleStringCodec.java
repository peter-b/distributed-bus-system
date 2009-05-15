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

package uk.ac.cam.dbs.bundle;

/** <p>ASCII string codec for encoding and decoding bundle
 * endpoints. Non-ASCII characters are replaced by '?'.</p>
 *
 * @see uk.ac.cam.dbs.bundle.Bundle#toBytes()
 * @see uk.ac.cam.dbs.bundle.Bundle#fromBytes(byte[], int, int)
 */
class BundleStringCodec {

    /** <p>Decode a nul-terminated string.</p>
     *
     * @param buf       Buffer from which to decode string.
     * @param offset    Offset of start of string within <code>buf</code>.
     * @param maxlength Maximum length of string if no nul found.
     */
    public static String fromTerminatedBytes(byte[] buf, int offset, int maxlength) {
        return fromBytes(buf, offset, maxlength);
    }

    /** <p>Decode a fixed-length string.</p>
     *
     * @param buf    Buffer from which to decode string.
     * @param offset Offset of start of string within <code>buf</code>.
     * @param length Length of string.
     */
    public static String fromBytes(byte[] buf, int offset, int length) {
        char[] c = new char[length];
        int i;
        for (i = 0; i < length; i++) {
            byte b = buf[offset+i];
            if (b == 0) break;
            if ((b & 128) != 0) {
                b = 0x3f; /* "?" */
            }
            c[i] = (char) b;
        }
        return new String(c, 0, i);
    }

    /** <p>Encode a string into a buffer.</p>
     *
     * @param str  String to encode.
     *
     * @return A byte array containing the encoded string.
     */
    public static byte[] toBytes(String str) {
        byte[] b = new byte[str.length()];
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c > 127) {
                c = 0x3f; /* "?" */
            }
            b[i] = (byte) c;
        }
        return b;
    }
}
