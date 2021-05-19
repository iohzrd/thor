package io.ipfs.blockservice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.format.Block;

public interface BlockGetter {
    @Nullable
    Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid, boolean root) throws ClosedException;

    void addBlock(@NonNull Block block);

    void preload(@NonNull Closeable closeable, @NonNull List<Cid> cids);
}
