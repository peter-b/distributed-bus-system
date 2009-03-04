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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/** <p>An RFC 4193 compatible 128-bit interface address. This address is
 * constructed according to the algorithm outlined in RFC 4193,
 * "Unique Local IPv6 Unicast Addresses", and as such is a valid IPv6
 * address.</p>
 *
 * <p>This is used by network nodes running on a general-purpose
 * computer in order to avoid interface address conflicts when
 * multiple nodes are running on the same hardware.</p>
 *
 * <p><b>Warning:</b> this algorithm will only return one unique
 * address per millisecond for the same MAC address.</p>
 */
public class Rfc4193InterfaceAddress extends InterfaceAddress {
    /** Creates an RFC 4193-compatible address. <code>mac</code>
     * should be a 48-bit or 64-bit hardware address, which is
     * converted to Modified EUI-64 format as described in RFC 3513,
     * "Internet Protocol Version 6 (IPv6) Addressing Architecture".
     * @param mac  48-bit or 64-bit hardware address.
     */
    public Rfc4193InterfaceAddress(byte[] mac) {
        super (makeBytes(mac));
    }

    private static byte[] makeBytes(byte[] mac) {
        if (mac == null)
            throw new NullPointerException("Null MAC address passed to Rfc4193InterfaceAddress(byte[])");

        /* Calculate a modified EUI64 address from the MAC that was
         * passed in */
        byte[] eui64 = null;
        switch (mac.length) {
        case 6:
            eui64 = new byte[8];
            for (int i = 0; i < 3; i++) {
                eui64[i] = mac[i];
            }
            eui64[3] = -1;
            eui64[4] = -2;
            for (int i = 3; i < 6; i++) {
                eui64[i+2] = mac[i];
            }
            break;

        case 8:
            eui64 = mac.clone();

        default:
            throw new IllegalArgumentException("A 48- or 64-bit MAC address is required");
        }
        eui64[0] ^= 0x2; /* invert global bit */

        /* Create a global ID */
        MessageDigest sha;
        Random r = new Random();
        try {
            sha = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        long now = r.nextLong();
        for (int i = 0; i < 8; i++) {
            int b = (int) (0xffL & (now >> i*8));
            sha.update((byte) ((b > 0x80) ? b - 0x100 : b));
        }
        sha.update(eui64);
        byte[] globSha = sha.digest();

        /* Use the fd00::/8 prefix, global ID, and EUI-64 to construct
         * an address. */
        byte[] address = new byte[16];
        address[0] = 0xfd - 0x100;
        for (int i = 0; i < 5; i++) {
            address[i+1] = globSha[globSha.length+i-5];
        }
        address[6] = 0;
        address[7] = 0;
        for (int i = 0; i < 8; i++) {
            address[i+8] = eui64[i];
        }

        return address;
    }
}
