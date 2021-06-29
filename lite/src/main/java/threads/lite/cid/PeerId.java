package threads.lite.cid;

import androidx.annotation.NonNull;

import com.google.common.primitives.UnsignedBytes;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;

import threads.lite.crypto.Key;
import threads.lite.crypto.PubKey;


public class PeerId implements Comparable<PeerId> {

    private final byte[] bytes;
    private long latency = Long.MAX_VALUE;

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


    @NonNull
    public static PeerId decodeName(@NonNull String name) {

        if (name.startsWith("Qm") || name.startsWith("1")) {
            // base58 encoded sha256 or identity multihash
            return PeerId.fromBase58(name);
        }
        byte[] data = Multibase.decode(name);

        if (data[0] == 0) {
            Multihash mh = new Multihash(Multihash.Type.id, data);
            return new PeerId(mh.getHash());
        } else {
            try (InputStream inputStream = new ByteArrayInputStream(data)) {
                long version = Multihash.readVarint(inputStream);
                if (version != 1) {
                    throw new Exception("invalid version");
                }
                long codecType = Multihash.readVarint(inputStream);
                if (!(codecType == Cid.DagProtobuf || codecType == Cid.Raw || codecType == Cid.Libp2pKey)) {
                    throw new Exception("not supported codec");
                }
                Multihash mh = Multihash.deserialize(inputStream);
                return PeerId.fromBase58(mh.toBase58());

            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }
    }

    public static PeerId fromBase58(String str) {
        return new PeerId(Base58.decode(str));
    }

    @NonNull
    public static PeerId fromPubKey(@NonNull PubKey pubKey) {

        byte[] pubKeyBytes = Key.marshalPublicKey(pubKey);
        if (pubKeyBytes.length <= 42) {
            byte[] hash = Cid.encode(pubKeyBytes, Multihash.Type.id.index);
            return PeerId.fromBase58(Base58.encode(hash));
        } else {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = Cid.encode(digest.digest(pubKeyBytes),
                        Multihash.Type.sha2_256.index);
                return PeerId.fromBase58(Base58.encode(hash));
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }
    }

    public String toBase32() {
        try {
            return Cid.NewCidV1(Cid.Libp2pKey, bytes).String();
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public long getLatency() {
        return latency;
    }

    public void setLatency(long latency) {
        this.latency = latency;
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
