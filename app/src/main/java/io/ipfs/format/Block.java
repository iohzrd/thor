package io.ipfs.format;

import androidx.annotation.NonNull;

import io.ipfs.cid.Cid;

public interface Block {
    byte[] getRawData();

    Cid getCid();

    @NonNull
    String toString();
}
