package io.ipfs.offline;

import androidx.annotation.NonNull;

import java.util.List;

import io.Closeable;
import io.ipfs.bitswap.BitSwapMessage;
import io.ipfs.cid.Cid;
import io.ipfs.exchange.Interface;
import io.ipfs.format.Block;
import io.ipfs.format.BlockStore;
import io.libp2p.core.PeerId;

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
    public void loadBlocks(@NonNull Closeable closeable, @NonNull List<Cid> preload) {
        // nothing to do here
    }


    @Override
    public void reset() {
        // nothing to do here
    }

    @Override
    public void load(@NonNull Closeable closeable, @NonNull Cid cid) {
        // nothing to do here
    }

    @Override
    public void ReceiveMessage(@NonNull PeerId peer, @NonNull String protocol, @NonNull BitSwapMessage incoming) {

    }

    @Override
    public void ReceiveError(@NonNull PeerId peer, @NonNull String protocol, @NonNull String error) {

    }

    @Override
    public boolean GatePeer(PeerId peerID) {
        return false;
    }
}
