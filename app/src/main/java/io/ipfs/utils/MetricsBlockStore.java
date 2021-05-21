package io.ipfs.utils;

import androidx.annotation.NonNull;

import java.util.List;

import io.ipfs.cid.Cid;
import io.ipfs.format.Block;
import io.ipfs.format.BlockStore;
import io.ipfs.format.Metrics;

public class MetricsBlockStore implements BlockStore {

    private final BlockStore blockstore;
    private final Metrics metrics;

    public MetricsBlockStore(@NonNull BlockStore blockstore, @NonNull Metrics metrics) {
        this.blockstore = blockstore;
        this.metrics = metrics;
    }

    @Override
    public boolean Has(@NonNull Cid cid) {
        return blockstore.Has(cid);
    }

    @Override
    public Block Get(@NonNull Cid cid) {
        Block block = blockstore.Get(cid);
        if (block != null) {
            metrics.seeding(block.getRawData().length);
        }
        return block;
    }

    @Override
    public void DeleteBlock(@NonNull Cid cid) {
        blockstore.DeleteBlock(cid);
    }

    @Override
    public void DeleteBlocks(@NonNull List<Cid> cids) {
        blockstore.DeleteBlocks(cids);
    }

    @Override
    public void Put(@NonNull Block block) {
        metrics.leeching(block.getRawData().length);
        blockstore.Put(block);
    }

    @Override
    public int GetSize(@NonNull Cid cid) {
        return blockstore.GetSize(cid);
    }
}
