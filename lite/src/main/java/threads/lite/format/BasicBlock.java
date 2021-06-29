package threads.lite.format;

import androidx.annotation.NonNull;

import java.security.MessageDigest;

import threads.lite.cid.Cid;

public class BasicBlock implements Block {

    private final Cid cid;
    private final byte[] data;

    public BasicBlock(@NonNull Cid cid, @NonNull byte[] data) {
        this.cid = cid;
        this.data = data;
    }

    public static Block createBlockWithCid(@NonNull Cid cid, @NonNull byte[] data) {
        return new BasicBlock(cid, data);
    }

    public static Block createBlock(@NonNull byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            Cid cid = Cid.NewCidV0(hash);
            return createBlockWithCid(cid, data);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    @Override
    public byte[] getRawData() {
        return data;
    }

    @Override
    public Cid getCid() {
        return cid;
    }

}
