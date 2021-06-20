package io.ipfs.core;


import androidx.annotation.NonNull;

import java.util.List;

import io.ipfs.cid.Cid;
import io.ipfs.format.Block;

public interface Interface {
    void reset();

    Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid, boolean root) throws ClosedException;

    void preload(@NonNull Closeable closeable, @NonNull List<Cid> cids);
}
