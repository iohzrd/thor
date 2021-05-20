package io.ipfs.offline;

import androidx.annotation.NonNull;

import java.util.List;

import io.ipfs.bitswap.BitSwapMessage;
import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.exchange.Interface;
import io.ipfs.format.Block;
import io.ipfs.format.BlockStore;
import io.ipfs.host.PeerId;

public class Exchange implements Interface {
    private final BlockStore blockstore;

    public Exchange(@NonNull BlockStore blockstore) {
        this.blockstore = blockstore;
    }

    @Override
    public Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid, boolean root) {
        return blockstore.Get(cid);
    }

    @Override
    public void preload(@NonNull Closeable closeable, @NonNull List<Cid> preload) {
        // nothing to do here
    }


    @Override
    public void reset() {
        // nothing to do here
    }

    @Override
    public void loadProviders(@NonNull Closeable closeable, @NonNull Cid cid) {
        // nothing to do here
    }

    @Override
    public boolean gatePeer(@NonNull PeerId peerID) {
        // nothing to do here
        return false;
    }

    @Override
    public void receiveMessage(@NonNull PeerId peer, @NonNull BitSwapMessage incoming) {
        // nothing to do here
    }
}
