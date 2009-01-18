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
            throw new ArithmeticException("SDNVs of more than 9 octets are not supported");
        return x;
    }
}
