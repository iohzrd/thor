package io.ipfs.blockservice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import io.core.Closeable;
import io.core.ClosedException;
import io.ipfs.cid.Cid;
import io.ipfs.format.Block;

public interface BlockGetter {
    @Nullable
    Block GetBlock(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException;

    void AddBlock(@NonNull Block block);

    void LoadBlocks(@NonNull Closeable closeable, @NonNull List<Cid> cids);
}
