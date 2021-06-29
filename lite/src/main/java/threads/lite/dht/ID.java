package threads.lite.dht;

import androidx.annotation.NonNull;

import com.google.common.primitives.UnsignedBytes;

import java.security.MessageDigest;

import threads.lite.cid.PeerId;

public class ID implements Comparable<ID> {
    public final byte[] data;

    public ID(@NonNull byte[] data) {
        this.data = data;
    }

    @NonNull
    public static ID convertPeerID(@NonNull PeerId id) {
        return convertKey(id.getBytes());
    }

    @NonNull
    public static ID convertKey(@NonNull byte[] id) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new ID(digest.digest(id));
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public static ID xor(@NonNull ID a, @NonNull ID b) {
        byte[] res = Util.xor(a.data, b.data);
        return new ID(res);
    }

    @Override
    public int compareTo(@NonNull ID o) {
        return UnsignedBytes.lexicographicalComparator().compare(this.data, o.data);
    }
}
