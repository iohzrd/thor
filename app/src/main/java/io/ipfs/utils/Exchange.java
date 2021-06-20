package io.ipfs.utils;

import androidx.annotation.NonNull;

import java.util.List;

import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.Interface;
import io.ipfs.format.Block;
import io.ipfs.format.BlockStore;

public class Exchange implements Interface {
    private final BlockStore blockstore;

    public Exchange(@NonNull BlockStore blockstore) {
        this.blockstore = blockstore;
    }

    @Override
    public Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid, boolean root) {
        return blockstore.getBlock(cid);
    }

    @Override
    public void preload(@NonNull Closeable closeable, @NonNull List<Cid> preload) {
        // nothing to do here
    }


    @Override
    public void reset() {
        // nothing to do here
    }

}
