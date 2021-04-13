package io.dht;

import androidx.annotation.NonNull;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

public class QueryKey {


    // Original is the original value of the identifier
    byte[] Original;

    // Bytes is the new value of the identifier, in the KeySpace.
    byte[] Bytes;

    private QueryKey(@NonNull byte[] original, @NonNull byte[] bytes) {

        this.Original = original;
        this.Bytes = bytes;
    }

    // Key converts an identifier into a Key in this space.
    public static QueryKey createQueryKey(byte[] id) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] key = digest.digest(id);
            return new QueryKey(id, key);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public BigInteger Distance(@NonNull QueryKey key) {
        // XOR the keys
        byte[] k3 = Util.xor(this.Bytes, key.Bytes);

        return new BigInteger(k3);
    }
}
