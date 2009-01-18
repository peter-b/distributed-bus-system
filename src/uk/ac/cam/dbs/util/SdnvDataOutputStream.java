package uk.ac.cam.dbs.util;

import java.io.*;

public class SdnvDataOutputStream
    extends DataOutputStream
{
    public SdnvDataOutputStream(OutputStream out)
    {
        super(out);
    }

    public void writeSdnv(long v)
        throws IOException, ArithmeticException
    {
        if (v < 0)
            throw new IllegalArgumentException("SDNVs may only encode positive integers");

        boolean foundMsb = false;
        for (int i = 9; i >= 0; i--) {
            int b = (int) (v >> (i*7)) & 127;
            if ((b == 0) && !foundMsb && (i > 0)) continue;

            foundMsb = true;
            b |= (i > 0) ? 128 : 0;
            writeByte(b);
        }
    }
}
