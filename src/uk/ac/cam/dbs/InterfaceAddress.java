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

import static uk.ac.cam.dbs.util.ByteBufferHelper.numToBytes;
import static uk.ac.cam.dbs.util.ByteBufferHelper.numFromBytes;

/** <p>An 128-bit interface address. These are notated in the same way
 * as IPv6 addresses, but <em>they are not IPv6 addresses</em>!</p>
 *
 * <p>The <code>InterfaceAddress(String)</code> and
 * <code>toString()</code> functions do not support the abbreviated
 * IPv6 address form (for instance, <code>::1</code> is not
 * understood: <code>0:0:0:0:0:0:0:1</code> should be used instead).</p>
 */
public class InterfaceAddress {

    private byte[] bytes;

    public InterfaceAddress(byte[] ipv6) {
        if (ipv6 == null)
            throw new NullPointerException();
        if (ipv6.length != 16)
            throw new IllegalArgumentException();
        bytes = ipv6.clone();
    }

    public InterfaceAddress(String ipv6) {
        byte[] addr = new byte[16];

        int i = ipv6.length() - 1;
        int j = 0; /* number of address bytes parsed */
        int wlen = 0; /* nibbles seen so far in word */
        while (i >= 0) {
            if (j >= 16)
                throw new IllegalArgumentException("Expected 128-bit address.");
            char c = ipv6.charAt(i);
            if (c != ':') {
                wlen++;
            }
            if (c == ':') {
                String wstr = ipv6.substring(i+1, i+wlen+1);
                int word = Integer.parseInt(wstr, 16);
                j += 2;
                numToBytes (word, addr, 16-j, 2);
                wlen = 0;
            }
            if (i <= 0) {
                String wstr = ipv6.substring(i, i+wlen);
                int word = Integer.parseInt(wstr, 16);
                j += 2;
                numToBytes (word, addr, 16-j, 2);
                wlen = 0;
            }
            i--;
        }
        if (j < 16) {
            throw new IllegalArgumentException("Expected 128-bit address.");
        }

        bytes = addr;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof InterfaceAddress)) return false;
        InterfaceAddress x = (InterfaceAddress) obj;

        byte[] a = getBytes();
        byte[] b = x.getBytes();
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return false;
        }
        return true;
    }

    public int hashCode() {
        int hash = 0;
        byte[] addr = getBytes();
        for (int i = 0; i < 4; i++) {
            int word = (int) numFromBytes(addr, i * 4, 4);
            hash ^= word;
        }
        return hash;
    }

    public String toString() {
        char[] buf = new char[39];
        byte[] addr = getBytes();
        int firstchar = buf.length;

        for (int i = 7; i >= 0; i--) {
            int word = (int) numFromBytes(addr, i * 2, 2);
            if (word < 0) word += 0x10000;
            if (word == 0) {
                buf[--firstchar] = '0';
            } else {
                while (word > 0) {
                    buf[--firstchar] = hexchar(word);
                    word = word >> 4;
                }
            }
            if (i > 0) {
                buf[--firstchar] = ':';
            }
        }

        return new String(buf, firstchar, buf.length - firstchar);
    }

    private char hexchar (int nibble) {
        int x = 0xf & nibble;
        if (x < 10) {
            return (char) ('0' + x);
        }
        if (x < 16) {
            return (char) ('a' + x - 10);
        }
        return '?';
    }
}
