package io.ipfs.bitswap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.luminis.quic.QuicClientConnection;
import net.luminis.quic.stream.QuicStream;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.cid.Cid;
import io.ipfs.cid.PeerId;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.core.ConnectionIssue;
import io.ipfs.core.ProtocolIssue;
import io.ipfs.core.TimeoutIssue;
import io.ipfs.format.Block;
import io.ipfs.format.BlockStore;
import io.ipfs.host.Connection;
import io.ipfs.host.LiteHost;
import io.ipfs.utils.DataHandler;


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
            long time = System.currentTimeMillis();
            boolean success = false;
            host.protectPeer(peerId, host.getShortTime());
            try {

                Connection conn = host.connectTo(closeable, peerId, IPFS.CONNECT_TIMEOUT);

                if (closeable.isClosed()) {
                    throw new ClosedException();
                }

                BitSwapSend stream = getStream(closeable, conn, priority);
                stream.writeAndFlush(DataHandler.encode(message.ToProtoV1()));
                stream.closeOutputStream();

                success = true;
            } catch (ClosedException | ConnectionIssue exception) {
                throw exception;
            } catch (Throwable throwable) {
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


    private CompletableFuture<BitSwapSend> getStream(@NonNull Connection connection,
                                                     @NonNull QuicClientConnection quicClientConnection,
                                                     short priority) {


        CompletableFuture<BitSwapSend> stream = new CompletableFuture<>();

        try {
            QuicStream quicStream = quicClientConnection.createStream(true);
            BitSwapSend bitSwapSend = new BitSwapSend(connection.remoteId(), quicStream, stream);

            // TODO streamChannel.updatePriority(new QuicStreamPriority(priority, false));

            bitSwapSend.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
            bitSwapSend.writeAndFlush(DataHandler.writeToken(IPFS.BITSWAP_PROTOCOL));

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            stream.completeExceptionally(throwable);
        }

        return stream;
    }

    private BitSwapSend getStream(@NonNull Closeable closeable, @NonNull Connection conn,
                                  short priority)
            throws InterruptedException, ExecutionException, ClosedException {

        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        QuicClientConnection quicChannel = conn.channel();

        CompletableFuture<BitSwapSend> ctrl = getStream(conn, quicChannel, priority);


        while (!ctrl.isDone()) {
            if (closeable.isClosed()) {
                ctrl.cancel(true);
            }
        }

        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        BitSwapSend stream = ctrl.get();

        Objects.requireNonNull(stream);

        return stream;
    }

}

