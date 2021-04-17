package io.dht;

import androidx.annotation.NonNull;

import java.security.MessageDigest;

import io.libp2p.core.PeerId;

public class Util {

    private static final short[] len8tab = new short[]{
            0x00, 0x01, 0x02, 0x02, 0x03, 0x03, 0x03, 0x03, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06,
            0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06,
            0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07,
            0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07,
            0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07,
            0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07,
            0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08,
            0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08,
            0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08,
            0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08,
            0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08,
            0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08,
            0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08,
            0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08,
    };


    // Len8 returns the minimum number of bits required to represent x; the result is 0 for x == 0.
    public static int Len8(int x) {
        return len8tab[x];
    }


    // LeadingZeros8 returns the number of leading zero bits in x; the result is 8 for x == 0.
    public static int LeadingZeros8(int uint8) {
        return 8 - Len8(uint8);
    }

    public static ID xor(@NonNull ID a, @NonNull ID b) {
        byte[] res = xor(a.data, b.data);
        return new ID(res);
    }

    public static int CommonPrefixLen(ID a, ID b) {
        byte[] res = xor(a.data, b.data);
        return ZeroPrefixLen(res);
    }


    public static byte[] xor(byte[] x1, byte[] x2) {
        byte[] out = new byte[x1.length];

        for (int i = 0; i < x1.length; i++) {
            out[i] = (byte) (0xff & ((int) x1[i]) ^ ((int) x2[i]));
        }
        return out;
    }

    // ZeroPrefixLen returns the number of consecutive zeroes in a byte slice.
    public static int ZeroPrefixLen(byte[] id) {
        for (int i = 0; i < id.length; i++) {
            byte b = id[i];
            if (b != 0) {
                int uint8 = b & 0xFF;
                return i * 8 + LeadingZeros8(uint8);
            }
        }
        return id.length * 8;

    }

    @NonNull
    public static ID ConvertPeerID(@NonNull PeerId id) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new ID(digest.digest(id.getBytes()));
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    @NonNull
    public static ID ConvertKey(@NonNull byte[] id) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new ID(digest.digest(id));
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
}
