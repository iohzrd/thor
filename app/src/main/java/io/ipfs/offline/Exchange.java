package io.ipfs.offline;

import androidx.annotation.NonNull;

import io.Closeable;
import io.ipfs.cid.Cid;
import io.ipfs.exchange.Interface;
import io.ipfs.format.Block;
import io.ipfs.format.BlockStore;

public class Exchange implements Interface {
    private final BlockStore blockstore;

    public Exchange(@NonNull BlockStore blockstore) {
        this.blockstore = blockstore;
    }

    @Override
    public Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid) {
        return blockstore.Get(cid);
    }


    @Override
    public void reset() {
        // nothing to do here
    }
}
