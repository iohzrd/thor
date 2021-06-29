package threads.lite.utils;

import androidx.annotation.NonNull;

import java.util.List;

import threads.lite.cid.Cid;
import threads.lite.format.Block;
import threads.lite.format.BlockStore;
import threads.lite.format.Metrics;

public class MetricsBlockStore implements BlockStore {

    private final BlockStore blockstore;
    private final Metrics metrics;

    public MetricsBlockStore(@NonNull BlockStore blockstore, @NonNull Metrics metrics) {
        this.blockstore = blockstore;
        this.metrics = metrics;
    }

    @Override
    public boolean hasBlock(@NonNull Cid cid) {
        return blockstore.hasBlock(cid);
    }

    @Override
    public Block getBlock(@NonNull Cid cid) {
        Block block = blockstore.getBlock(cid);
        if (block != null) {
            metrics.seeding(block.getRawData().length);
        }
        return block;
    }

    @Override
    public void deleteBlock(@NonNull Cid cid) {
        blockstore.deleteBlock(cid);
    }

    @Override
    public void deleteBlocks(@NonNull List<Cid> cids) {
        blockstore.deleteBlocks(cids);
    }

    @Override
    public void putBlock(@NonNull Block block) {
        metrics.leeching(block.getRawData().length);
        blockstore.putBlock(block);
    }

    @Override
    public int getSize(@NonNull Cid cid) {
        return blockstore.getSize(cid);
    }
}
