package io.ipfs.bitswap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import io.Closeable;
import io.LogUtils;
import io.ipfs.cid.Cid;
import io.ipfs.exchange.Interface;
import io.ipfs.format.Block;
import io.ipfs.format.BlockStore;
import io.libp2p.peer.PeerID;
import io.libp2p.protocol.Protocol;

public class BitSwap implements Interface, Receiver {

    private static final String TAG = BitSwap.class.getSimpleName();

    private final BitSwapEngine engine;
    private final ContentManager contentManager;


    public BitSwap(@NonNull BlockStore blockstore, @NonNull BitSwapNetwork network) {
        engine = BitSwapEngine.NewEngine(blockstore, network, network.Self());
        contentManager = new ContentManager(blockstore, network);
    }

    public static Interface New(@NonNull BitSwapNetwork bitSwapNetwork, @NonNull BlockStore blockstore) {

        BitSwap bitSwap = new BitSwap(blockstore, bitSwapNetwork);
        bitSwapNetwork.SetDelegate(bitSwap);
        return bitSwap;
    }

    @Nullable
    @Override
    public Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid) {
        return contentManager.GetBlock(closeable, cid);
    }

    @Override
    public void loadBlocks(@NonNull Closeable closeable, @NonNull List<Cid> cids) {
        contentManager.LoadBlocks(closeable, cids);
    }

    public void reset() {
        contentManager.reset();
    }

    @Override
    public void ReceiveMessage(@NonNull PeerID peer, @NonNull Protocol protocol, @NonNull BitSwapMessage incoming) {

        LogUtils.verbose(TAG, "ReceiveMessage " + peer.String() + " " + protocol.String());

        List<Block> blocks = incoming.Blocks();
        List<Cid> haves = incoming.Haves();
        if (blocks.size() > 0 || haves.size() > 0) {
            // Process blocks
            try {
                receiveBlocksFrom(peer, blocks, haves);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }

        //engine.MessageReceived(peer, protocol, incoming);
    }


    private void receiveBlocksFrom(@NonNull PeerID peer,
                                   @NonNull List<Block> wanted,
                                   @NonNull List<Cid> haves) {


        LogUtils.error(TAG, "ReceiveBlocks " + peer.String());

        for (Block block : wanted) {
            contentManager.BlockReceived(peer, block);
        }

        contentManager.HaveReceived(peer, haves);


    }

    @Override
    public void ReceiveError(@NonNull PeerID peer, @NonNull Protocol protocol, @NonNull String error) {

        // TODO handle error
        LogUtils.error(TAG, "ReceiveError " + peer.String() + " " + protocol.String() + " " + error);
    }

    @Override
    public boolean GatePeer(PeerID peerID) {
        return contentManager.GatePeer(peerID);
    }


}

