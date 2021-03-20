package io.ipfs.format;

import androidx.annotation.NonNull;

import java.security.MessageDigest;

import io.ipfs.cid.Cid;

public class BasicBlock implements Block {

    private final Cid cid;
    private final byte[] data;

    public BasicBlock(@NonNull Cid cid, @NonNull byte[] data) {
        this.cid = cid;
        this.data = data;
    }

    public static Block NewBlockWithCid(@NonNull Cid cid, @NonNull byte[] data) {
        return new BasicBlock(cid, data);
    }

    public static Block NewBlock(@NonNull byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            Cid cid = Cid.NewCidV0(hash); // TODO why
            return NewBlockWithCid(cid, data);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    @Override
    public byte[] RawData() {
        return data;
    }

    @Override
    public Cid Cid() {
        return cid;
    }

}
