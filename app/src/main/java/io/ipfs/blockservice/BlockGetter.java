package io.ipfs.blockservice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.Closeable;
import io.ipfs.cid.Cid;
import io.ipfs.format.Block;

public interface BlockGetter {
    @Nullable
    Block GetBlock(@NonNull Closeable closeable, @NonNull Cid cid);

    void AddBlock(@NonNull Block block);

}
