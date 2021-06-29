package threads.lite.bitswap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.luminis.quic.QuicClientConnection;
import net.luminis.quic.stream.QuicStream;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.Cid;
import threads.lite.cid.PeerId;
import threads.lite.core.Closeable;
import threads.lite.core.ClosedException;
import threads.lite.core.ConnectionIssue;
import threads.lite.core.ProtocolIssue;
import threads.lite.core.TimeoutIssue;
import threads.lite.format.Block;
import threads.lite.format.BlockStore;
import threads.lite.host.LiteHost;
import threads.lite.utils.DataHandler;


public class BitSwap implements Interface {

    private static final String TAG = BitSwap.class.getSimpleName();

    @NonNull
    private final ContentManager contentManager;
    @NonNull
    private final BitSwapEngine engine;
    @NonNull
    private final LiteHost host;

    public BitSwap(@NonNull BlockStore blockstore, @NonNull LiteHost host) {
        this.host = host;
        contentManager = new ContentManager(this, blockstore, host);
        engine = new BitSwapEngine(this, blockstore, host.self());
    }


    @Nullable
    @Override
    public Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid, boolean root) throws ClosedException {
        return contentManager.getBlock(closeable, cid, root);
    }

    @Override
    public void preload(@NonNull Closeable closeable, @NonNull List<Cid> cids) {
        contentManager.loadBlocks(closeable, cids);
    }

    @Override
    public void reset() {
        contentManager.reset();
    }


    public void receiveMessage(@NonNull PeerId peer, @NonNull BitSwapMessage incoming) {

        LogUtils.verbose(TAG, "ReceiveMessage " + peer.toBase58());

        List<Block> blocks = incoming.Blocks();
        List<Cid> haves = incoming.Haves();
        if (blocks.size() > 0 || haves.size() > 0) {
            try {
                LogUtils.debug(TAG, "ReceiveMessage " + peer.toBase58());
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
                    " " + block.getCid().String());
            contentManager.blockReceived(peer, block);
        }

        contentManager.haveReceived(peer, haves);

    }


    public boolean gatePeer(@NonNull PeerId peerID) {
        return contentManager.gatePeer(peerID);
    }


    public void writeMessage(@NonNull Closeable closeable, @NonNull PeerId peerId,
                             @NonNull BitSwapMessage message, short priority)
            throws ClosedException, ProtocolIssue, TimeoutIssue, ConnectionIssue {

        if (IPFS.BITSWAP_REQUEST_ACTIVE) {
            boolean success = false;
            host.protectPeer(peerId, host.getShortTime());

            QuicClientConnection conn = host.connectTo(closeable, peerId, IPFS.CONNECT_TIMEOUT);

            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            long time = System.currentTimeMillis();
            try {

                CompletableFuture<BitSwapSend> request = new CompletableFuture<>();

                QuicStream quicStream = conn.createStream(true,
                        IPFS.CONNECT_TIMEOUT, TimeUnit.SECONDS);
                BitSwapSend bitSwapSend = new BitSwapSend(peerId, quicStream, request);

                // TODO streamChannel.updatePriority(new QuicStreamPriority(priority, false));

                bitSwapSend.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
                bitSwapSend.writeAndFlush(DataHandler.writeToken(IPFS.BITSWAP_PROTOCOL));
                bitSwapSend.writeAndFlush(DataHandler.encode(message.ToProtoV1()));
                bitSwapSend.closeOutputStream();

                request.get(IPFS.CONNECT_TIMEOUT, TimeUnit.SECONDS);

                success = true;
            } catch (Throwable throwable) {
                LogUtils.error(TAG, "" + throwable);
                Throwable cause = throwable.getCause();
                if (cause != null) {
                    if (cause instanceof ProtocolIssue) {
                        throw new ProtocolIssue();
                    }
                    if (cause instanceof ConnectionIssue) {
                        throw new ConnectionIssue();
                    }
                    if (cause instanceof TimeoutException) {
                        throw new TimeoutIssue();
                    }
                }
                throw new RuntimeException(throwable);
            } finally {
                LogUtils.debug(TAG, "Send took " + success + " " +
                        peerId.toBase58() + " " + (System.currentTimeMillis() - time));
            }
        }
    }
}

