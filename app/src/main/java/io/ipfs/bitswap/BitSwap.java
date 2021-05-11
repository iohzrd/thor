package io.ipfs.bitswap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.exchange.Interface;
import io.ipfs.format.Block;
import io.ipfs.format.BlockStore;
import io.libp2p.core.PeerId;


public class BitSwap implements Interface {

    private static final String TAG = BitSwap.class.getSimpleName();

    private final ContentManager contentManager;
    private final BitSwapEngine engine;

    public BitSwap(@NonNull BlockStore blockstore, @NonNull BitSwapNetwork network) {
        contentManager = new ContentManager(blockstore, network);
        engine = BitSwapEngine.NewEngine(blockstore, network, network.Self());
    }

    public static Interface create(@NonNull BitSwapNetwork bitSwapNetwork,
                                   @NonNull BlockStore blockstore) {
        return new BitSwap(blockstore, bitSwapNetwork);
    }

    @Nullable
    @Override
    public Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException {
        return contentManager.GetBlock(closeable, cid);
    }

    @Override
    public void loadBlocks(@NonNull Closeable closeable, @NonNull List<Cid> cids) {
        contentManager.LoadBlocks(closeable, cids);
    }

    @Override
    public void reset() {
        contentManager.reset();
    }

    @Override
    public void load(@NonNull Closeable closeable, @NonNull Cid cid) {
        contentManager.Load(closeable, cid);
    }

    @Override
    public void ReceiveMessage(@NonNull PeerId peer, @NonNull String protocol, @NonNull BitSwapMessage incoming) {

        LogUtils.info(TAG, "ReceiveMessage " + peer.toBase58() + " " + protocol);

        List<Block> blocks = incoming.Blocks();
        List<Cid> haves = incoming.Haves();
        if (blocks.size() > 0 || haves.size() > 0) {
            try {
                LogUtils.error(TAG, "ReceiveMessage " + peer.toBase58() + " " + protocol);
                receiveBlocksFrom(peer, blocks, haves);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }

        if (IPFS.BITSWAP_ENGINE_ACTIVE) {
            engine.MessageReceived(peer, incoming);
        }
    }


    private void receiveBlocksFrom(@NonNull PeerId peer,
                                   @NonNull List<Block> wanted,
                                   @NonNull List<Cid> haves) {


        for (Block block : wanted) {
            LogUtils.verbose(TAG, "ReceiveBlock " + peer.toBase58() +
                    " " + block.Cid().String());
            contentManager.BlockReceived(peer, block);
        }

        contentManager.HaveReceived(peer, haves);


    }

    @Override
    public void ReceiveError(@NonNull PeerId peer, @NonNull String protocol, @NonNull String error) {
        // TODO evaluate when this happens
        LogUtils.error(TAG, "ReceiveError " + peer.toBase58() + " " + protocol + " " + error);
    }

    @Override
    public boolean GatePeer(PeerId peerID) {
        return contentManager.GatePeer(peerID);
    }


}

