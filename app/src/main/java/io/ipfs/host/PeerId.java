package io.ipfs.host;

import androidx.annotation.NonNull;

import com.google.common.primitives.UnsignedBytes;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;

import io.ipfs.cid.Cid;
import io.ipfs.crypto.Key;
import io.ipfs.crypto.PubKey;
import io.ipfs.multibase.Base58;


public class PeerId implements Comparable<PeerId> {

    private final byte[] bytes;

    public PeerId(byte[] bytes) {
        this.bytes = bytes;
        if (this.bytes.length < 32 || this.bytes.length > 50) {
            throw new IllegalArgumentException("Invalid peerId length: " + this.bytes.length);
        }
    }

    public static PeerId random() {
        byte[] bytes = new byte[32];
        new Random().nextBytes(bytes);
        return new PeerId(bytes);
    }

    public static PeerId fromBase58(String str) {
        return new PeerId(Base58.decode(str));
    }

    @NonNull
    public static PeerId fromPubKey(@NonNull PubKey pubKey) {

        byte[] pubKeyBytes = Key.marshalPublicKey(pubKey);
        if (pubKeyBytes.length <= 42) {
            byte[] hash = Cid.Encode(pubKeyBytes, io.ipfs.multihash.Multihash.Type.id.index);
            return PeerId.fromBase58(Base58.encode(hash));
        } else {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = Cid.Encode(digest.digest(pubKeyBytes),
                        io.ipfs.multihash.Multihash.Type.sha2_256.index);
                return PeerId.fromBase58(Base58.encode(hash));
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }
    }


    @NonNull
    public final String toBase58() {
        return Base58.encode(this.bytes);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerId peer = (PeerId) o;
        return Arrays.equals(bytes, peer.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @NonNull
    public String toString() {
        return this.toBase58();
    }

    @NonNull
    public final byte[] getBytes() {
        return this.bytes;
    }

    @Override
    public int compareTo(PeerId o) {
        return UnsignedBytes.lexicographicalComparator().compare(this.bytes, o.bytes);
    }
}
