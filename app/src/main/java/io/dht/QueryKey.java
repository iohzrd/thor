package io.dht;

import androidx.annotation.NonNull;

import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;

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

    public long Distance(@NonNull QueryKey key) {
        // XOR the keys
        byte[] k3 = ByteUtils.xor(this.Bytes, key.Bytes);

        ByteBuffer wrapped = ByteBuffer.wrap(k3); // big-endian by default
        return wrapped.getLong(); // TODO check if correct

        // interpret it as an integer
        // dist := big.NewInt(0).SetBytes(k3);
        //return dist
    }
}
