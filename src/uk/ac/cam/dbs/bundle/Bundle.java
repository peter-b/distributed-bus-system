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

/* FIXME override hashcode() */

package uk.ac.cam.dbs.bundle;

import static uk.ac.cam.dbs.util.SdnvByteBufferHelper.*;
import static uk.ac.cam.dbs.util.ByteBufferHelper.*;

/** <p>A bundling protocol data bundle.</p> */
public class Bundle {

    /** Supported bundling protocol version. */
    public static final byte VERSION = 0x06;

    public static final int FLAG_ADMIN = 1<<1;
    public static final int FLAG_CUSTODY = 1<<3;

    private static final int EP_SOURCE = 0;
    private static final int EP_DEST = 1;
    private static final int EP_REPORT = 2;
    private static final int EP_CUSTODIAN = 3;

    /* We only allow one non-primary block type, the payload
     * block. It's always the last block, so we set that flag. */
    private static final int PAYLOAD_BLOCK_FLAGS = 1 << 3;
    private static final int PAYLOAD_BLOCK_TYPE = 1;

    private String[] endpoints;
    private int flags;
    private long timestamp;
    private long seq;
    private long lifetime;

    private byte[] payload;

    /** Create a new, empty bundle. */
    public Bundle() {
        String nullEndpoint = "dtn:none";
        endpoints = new String[] {nullEndpoint, nullEndpoint,
                                  nullEndpoint, nullEndpoint};
        flags = 0;

        timestamp = 0;
        seq = 0;
        lifetime = 0;
        payload = null;
    }

    /** <p>Create a new <code>Bundle</code> by parsing a byte buffer.</p>
     *
     * <p>Equivalent to:</p>
     *
     * <pre>Bundle(buf, 0, buf.length</pre>
     *
     * @param buf Buffer to parse bundle data from.
     */
    public Bundle(byte[] buf) {
        this(buf, 0, buf.length);
    }

    /** <p>Create a new <code>Bundle</code> by parsing a byte buffer.</p>
     *
     * <p><strong>Warning:</strong> The <code>length</code> parameter
     * is currently ignored.</p>
     *
     * @param buf    Buffer to parse bundle data from.
     * @param offset Offset within buffer to start parsing.
     * @param length Maximum length of data to use.
     */
    public Bundle(byte[] buf, int offset, int length) {
        this();
        fromBytes(buf, offset, length);
    }

    /** Get the bundle processing control flags. */
    public int getFlags() {
        return flags;
    }

    /** Set the bundle processing control flags. */
    public void setFlags(int flags) {
        this.flags = flags;
    }

    /** Get the bundle creation timestamp. This is in seconds after
     * 2000-01-01 00:00 UTC. */
    public long getTimestamp() {
        return timestamp;
    }

    /** Set the bundle creation timestamp. This is in seconds after
     * 2000-01-01 00:00 UTC. */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /** Get the bundle creation sequence number. */
    public long getSequence() {
        return seq;
    }

    /** Set the bundle creation sequence number. */
    public void setSequence(int seq) {
        this.seq = seq;
    }

    /** Get the bundle lifetime. This is in seconds after creation. */
    public long getLifetime() {
        return lifetime;
    }

    /** Set the bundle lifetime. This is in seconds after creation. */
    public void setLifetime(long lifetime) {
        this.lifetime = lifetime;
    }

    /** Get the source endpoint. */
    public String getSourceEndpoint() {
        return endpoints[EP_SOURCE];
    }

    /** Set the source endpoint. */
    public void setSourceEndpoint(String endpoint) {
        endpoints[EP_SOURCE] = endpoint;
    }

    /** Get the destination endpoint. */
    public String getDestEndpoint() {
        return endpoints[EP_DEST];
    }

    /** Set the destination endpoint. */
    public void setDestEndpoint(String endpoint) {
        endpoints[EP_DEST] = endpoint;
    }

    /** Get the report-to endpoint. */
    public String getReportToEndpoint() {
        return endpoints[EP_REPORT];
    }

    /** Set the report-to endpoint. */
    public void setReportToEndpoint(String endpoint) {
        endpoints[EP_REPORT] = endpoint;
    }

    /** Get the current custodian endpoint. */
    public String getCustodianEndpoint() {
        return endpoints[EP_CUSTODIAN];
    }

    /** Set the current custodian endpoint. */
    public void setCustodianEndpoint(String endpoint) {
        endpoints[EP_CUSTODIAN] = endpoint;
    }

    /** Get the bundle payload. */
    public byte[] getPayload() {
        return payload;
    }

    /** Set the bundle payload. */
    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    /** Format the bundle as a byte buffer for transmission. */
    public byte[] toBytes() {
        /* 1. Calculate dictionary length & offsets. */
        /* FIXME this does not detect duplicate strings in the
         * dictionary. */
        String[] dictStrings = new String[8];
        int[] dictOffsets = new int[8];
        int dictSize = 0; /* Including null bytes */
        for (byte i = 0; i < 4; i++) {
            String[] parts = splitEndpoint(endpoints[i]);
            dictOffsets[2*i] = dictSize;
            dictSize += parts[0].length() + 1;
            dictStrings[2*i] = parts[0];
            dictOffsets[2*i+1] = dictSize;
            dictSize += parts[1].length() + 1;
            dictStrings[2*i+1] = parts[1];
        }

        /* 2. Calculate SDNV lengths. There are 14 SDNVs in the
         * primary block, including the block length (which can only
         * be calculated after all the other SDNV lengths have been
         * calculated) and the flags SDNV. There are 2 SDNVs in the payload block. */
        byte[] sdnvSizes = new byte[16];
        sdnvSizes[0] = (byte) getSdnvLength(flags); /* Flags */
        for (byte i = 0; i < 8; i++) { /* Dictionary offsets, x8 */
            sdnvSizes[i+2] = (byte) getSdnvLength(dictOffsets[i]);
        }
        sdnvSizes[10] = (byte) getSdnvLength(timestamp);
        sdnvSizes[11] = (byte) getSdnvLength(seq);
        sdnvSizes[12] = (byte) getSdnvLength(lifetime);
        sdnvSizes[13] = (byte) getSdnvLength(dictSize);

        sdnvSizes[14] = (byte) getSdnvLength(PAYLOAD_BLOCK_FLAGS);
        sdnvSizes[15] = (byte) getSdnvLength(payload.length);
        /* Calculate the total size of all of the primary block
         * SDNVs. We need this to calculate the primary block length
         * field. */
        int primaryBlockLength = 0;
        for (byte i = 2; i < 14; i++) {
            primaryBlockLength += sdnvSizes[i];
        }
        primaryBlockLength += dictSize;

        /* We can now calculate the size of the SDNV used to hold block length */
        sdnvSizes[1] = (byte) getSdnvLength(primaryBlockLength);

        /* 3. Calculate length of & allocate result buffer */
        int bundleSize = 1; /* Length of version octet */
        for (byte i = 0; i < 16; i++) { /* SDNVs */
            bundleSize += sdnvSizes[i];
        }
        bundleSize += dictSize + payload.length + 1; /* Dictionary & payload & payload block type */
        byte[] result = new byte[bundleSize];

        /* 4. Copy strings & SDNVs into buffer */
        int resultOfs = 0;

        /* Primary block */
        result[resultOfs++] = VERSION; /* Bundle protocol version */
        resultOfs += sdnvToBytes(flags, result, resultOfs);
        resultOfs += sdnvToBytes(primaryBlockLength, result, resultOfs);
        for (byte i = 0; i < 8; i++) {
            resultOfs += sdnvToBytes(dictOffsets[i], result, resultOfs);
        }
        resultOfs += sdnvToBytes(timestamp, result, resultOfs);
        resultOfs += sdnvToBytes(seq, result, resultOfs);
        resultOfs += sdnvToBytes(lifetime, result, resultOfs);
        resultOfs += sdnvToBytes(dictSize, result, resultOfs);

        /* Copy endpoint dictionary into buffer */
        for (byte i = 0; i < dictStrings.length; i++) {
            byte[] ascii = BundleStringCodec.toBytes(dictStrings[i]);
            for (int j = 0; j < ascii.length; j++) {
                result[resultOfs++] = ascii[j];
            }
            result[resultOfs++] = 0; /* Null terminate each string */
        }

        /* Payload block */
        result[resultOfs++] = PAYLOAD_BLOCK_TYPE;
        resultOfs += sdnvToBytes(PAYLOAD_BLOCK_FLAGS, result, resultOfs);
        resultOfs += sdnvToBytes(payload.length, result, resultOfs);
        /* Copy payload into buffer */
        for (int i = 0; i < payload.length; i++) {
            result[resultOfs++] = payload[i];
        }

        return result;
    }

    /** Used by the Bundle(byte[], int, int) constructor. */
    private void fromBytes(byte[] buf, int off, int len) {
        int offset = off;

        /* Primary block */
        byte version = buf[offset++];
        if (version != VERSION)
            throw new IllegalArgumentException("Unrecognized bundle version");

        flags = (int) sdnvFromBytes(buf, offset);
        offset += getSdnvLength(buf, offset);

        int primaryBlockLength = (int) sdnvFromBytes(buf, offset);
        offset += getSdnvLength(buf, offset);

        int[] dictOffsets = new int[8];
        for (byte i = 0; i < 8; i++) {
            dictOffsets[i] = (int) sdnvFromBytes(buf, offset);
            offset += getSdnvLength(buf, offset);
        }

        timestamp = sdnvFromBytes(buf, offset);
        offset += getSdnvLength(buf, offset);
        seq = sdnvFromBytes(buf, offset);
        offset += getSdnvLength(buf, offset);
        lifetime = sdnvFromBytes(buf, offset);
        offset += getSdnvLength(buf, offset);

        int dictLength = (int) sdnvFromBytes(buf, offset);
        offset += getSdnvLength(buf, offset);
        int dictOffset = offset;
        offset += dictLength;

        /* Payload block */
        int blockType = buf[offset++];
        if (blockType != PAYLOAD_BLOCK_TYPE)
            throw new IllegalArgumentException("Unrecognized block type");

        int blockFlags = (int) sdnvFromBytes(buf, offset);
        offset += getSdnvLength(buf, offset);
        if (blockFlags != PAYLOAD_BLOCK_FLAGS)
            throw new IllegalArgumentException("Unrecognized block flags");

        int payloadLength = (int) sdnvFromBytes(buf, offset);
        offset += getSdnvLength(buf, offset);

        payload = new byte[payloadLength];
        for (int i = 0; i < payloadLength; i++) {
            payload[i] = buf[offset++];
        }

        /* Assemble endpoint strings */
        for (byte i = 0; i < 4; i++) {
            String scheme = BundleStringCodec.fromTerminatedBytes(buf,
                                                dictOffset + dictOffsets[2*i],
                                                dictLength - dictOffsets[2*i]);
            String ssp = BundleStringCodec.fromTerminatedBytes(buf,
                                             dictOffset + dictOffsets[2*i+1],
                                             dictLength - dictOffsets[2*i+1]);
            endpoints[i] = scheme + ":" + ssp;
        }
    }

    /** Split an endpoint into scheme and scheme-specific parts. */
    private static String[] splitEndpoint(String ep) {
        int splitAt = ep.indexOf(':');
        if (splitAt < 0) {
            throw new IllegalArgumentException("Malformed endpoint: " + ep);
        }
        String[] results = new String[2];
        results[0] = ep.substring(0,splitAt);
        results[1] = ep.substring(splitAt+1);

        return results;
    }

    public boolean equals(Object o) {
        if (!(o instanceof Bundle)) return false;
        Bundle x = (Bundle) o;

        if ((getFlags() != x.getFlags())
            || (getTimestamp() != x.getTimestamp())
            || (getSequence() != x.getSequence())
            || (getLifetime() != x.getLifetime())
            || (!getSourceEndpoint().equals(x.getSourceEndpoint()))
            || (!getDestEndpoint().equals(x.getDestEndpoint()))
            || (!getReportToEndpoint().equals(x.getReportToEndpoint()))
            || (!getCustodianEndpoint().equals(x.getCustodianEndpoint()))) {
            return false;
        }

        byte[] payload = getPayload();
        byte[] payloadx = x.getPayload();
        if (payload.length != payloadx.length) return false;
        for (int i = 0; i < payload.length; i++) {
            if (payload[i] != payloadx[i]) return false;
        }

        return true;
    }

    public boolean idEquals(Bundle b) {
        return (getSourceEndpoint().equals(b.getSourceEndpoint())
                && (getTimestamp() == b.getTimestamp())
                && (getSequence() == b.getSequence()));
    }
}
