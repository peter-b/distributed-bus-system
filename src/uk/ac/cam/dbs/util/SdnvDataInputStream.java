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

import java.io.*;

public class SdnvDataInputStream
    extends DataInputStream
{
    public SdnvDataInputStream(InputStream in)
    {
        super(in);
    }

    public long readSdnv()
        throws IOException, ArithmeticException
    {
        long x = 0;
        int bits = 0;
        boolean overflow = false;
        for (int i = 0; ; i++) {
            byte b = readByte();
            x = (x << 7) + (b & 127);
            if ((b & 128) == 0) break;
            if (i >= 8) overflow = true;
        }
        if (overflow)
            throw new IOException("SDNVs of more than 9 octets are not supported");
        return x;
    }
}
