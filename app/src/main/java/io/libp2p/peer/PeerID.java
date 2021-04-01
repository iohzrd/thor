package io.libp2p.peer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Objects;

import io.ipfs.cid.Cid;
import io.ipfs.multibase.Base58;
import io.ipfs.multihash.Multihash;
import io.libp2p.core.crypto.PubKey;

public class PeerID implements Comparable<PeerID> {
    private final String id;

    public PeerID(@NonNull String id) {
        this.id = id;
    }

    public String String() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerID id1 = (PeerID) o;
        return Objects.equals(id, id1.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public int compareTo(PeerID o) {
        return id.compareTo(o.id);
    }


    private static String deserialize(byte[] raw) {
        try (InputStream inputStream = new ByteArrayInputStream(raw)) {
            return deserialize(inputStream);
        } catch (Throwable ignore) {
            return "";
        }

    }

    private static String deserialize(InputStream din) throws IOException {
        int type = (int) Multihash.readVarint(din);
        if (type != 1) {
            throw new RuntimeException();
        }
        Multihash.readVarint(din);
        ByteBuffer byteBuffer = ByteBuffer.allocate(din.available());

        int res = din.read(byteBuffer.array());
        if (res <= 0) {
            throw new RuntimeException();
        }

        return Base58.encode(byteBuffer.array());
    }


    @Nullable
    public static PeerID decode(@NonNull String name) {
        try {

            if (name.startsWith("Qm") || name.startsWith("1")) {
                // base58 encoded sha256 or identity multihash
                return new PeerID(Multihash.fromBase58(name).toBase58());
            }
            Cid cid = Cid.Decode(name);


            long ty = cid.Type();
            if (ty != Cid.Libp2pKey) {
                throw new RuntimeException("Decode name failed");
            }

            return new PeerID(deserialize(cid.Bytes()));


        } catch (Throwable ignore) {
            // ignore
        }
        return null;
    }

    public String base32() {
        try {
            // only support fromBase58
            Multihash multihash = Multihash.fromBase58(id);
            return Cid.NewCidV1(Cid.Libp2pKey, multihash.toBytes()).String();
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
    private static boolean AdvancedEnableInlining = true;

    private static int maxInlineKeyLength = 42;


    public static PeerID IDFromPublicKey(@NonNull PubKey pk) {

        byte[] data = pk.bytes();

        Multihash.Type alg = Multihash.Type.sha2_256;
        if (AdvancedEnableInlining && (data.length <= maxInlineKeyLength)) {
            alg = Multihash.Type.id;
        }
        try {
            if(alg == Multihash.Type.id) {
                Multihash multihash = new Multihash(Multihash.Type.id, data);
                return new PeerID(multihash.toBase58());
            } else {
               throw new RuntimeException("not yet supported (maybe not required)");
            }
        } catch (Throwable throwable){
            throw new RuntimeException();
        }
    }

}
