package io.ipfs.bitswap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.core.ConnectionIssue;
import io.ipfs.core.ProtocolIssue;
import io.ipfs.core.TimeoutIssue;
import io.ipfs.exchange.Interface;
import io.ipfs.format.Block;
import io.ipfs.format.BlockStore;
import io.ipfs.host.Connection;
import io.ipfs.host.LiteHost;
import io.ipfs.host.PeerId;
import io.ipfs.utils.DataHandler;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamPriority;
import io.netty.incubator.codec.quic.QuicStreamType;


public class BitSwap implements Interface {

    private static final String TAG = BitSwap.class.getSimpleName();

    @NonNull
    private final ContentManager contentManager;
    @NonNull
    private final BitSwapEngine engine;
    @NonNull
    private final LiteHost network;

    @NonNull
    public ConcurrentHashMap<QuicChannel, QuicStreamChannel> bitSwaps = new ConcurrentHashMap<>();

    public BitSwap(@NonNull BlockStore blockstore, @NonNull LiteHost network) {
        this.network = network;
        contentManager = new ContentManager(this, blockstore, network);
        engine = new BitSwapEngine(this, blockstore, network.Self());
    }

    public static Interface create(@NonNull LiteHost bitSwapNetwork,
                                   @NonNull BlockStore blockstore) {
        return new BitSwap(blockstore, bitSwapNetwork);
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

    @Override
    public void loadProvider(@NonNull Closeable closeable, @NonNull Cid cid) {
        contentManager.loadProvider(closeable, cid);
    }

    @Override
    public void receiveMessage(@NonNull PeerId peer, @NonNull BitSwapMessage incoming) {

        LogUtils.info(TAG, "ReceiveMessage " + peer.toBase58());

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
                    " " + block.Cid().String());
            contentManager.BlockReceived(peer, block);
        }

        contentManager.HaveReceived(peer, haves);


    }


    @Override
    public boolean gatePeer(@NonNull PeerId peerID) {
        return contentManager.GatePeer(peerID);
    }


    public void writeMessage(@NonNull Closeable closeable, @NonNull PeerId peer,
                             @NonNull BitSwapMessage message, short priority)
            throws ClosedException, ProtocolIssue, TimeoutIssue, ConnectionIssue {

        long time = System.currentTimeMillis();
        boolean success = false;
        network.protectPeer(peer);
        try {
            Connection conn = network.connect(closeable, peer, IPFS.CONNECT_TIMEOUT);

            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            QuicStreamChannel stream = getStream(closeable, conn, priority);
            stream.writeAndFlush(DataHandler.encode(message.ToProtoV1().toByteArray()));
            success = true;
        } catch (ClosedException | ConnectionIssue exception) {
            throw exception;
        } catch (Throwable throwable) {
            network.unprotectPeer(peer);
            Throwable cause = throwable.getCause();
            if (cause != null) {
                if (cause instanceof ProtocolIssue) {
                    throw new ProtocolIssue();
                }
                if (cause instanceof ConnectionIssue) {
                    throw new ConnectionIssue();
                }
                if (cause instanceof ReadTimeoutException) {
                    throw new TimeoutIssue();
                }
            }
            throw new RuntimeException(throwable);
        } finally {
            LogUtils.info(TAG, "Send took " + success + " " + (System.currentTimeMillis() - time));
        }
    }


    private CompletableFuture<QuicStreamChannel> getStream(@NonNull QuicChannel quicChannel, short priority) {


        CompletableFuture<QuicStreamChannel> stream = new CompletableFuture<>();

        try {
            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new BitSwapSend(stream, this)).sync().get();

            streamChannel.updatePriority(new QuicStreamPriority(priority, false));


            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.BIT_SWAP_PROTOCOL));

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            stream.completeExceptionally(throwable);
        }

        return stream;
    }

    private QuicStreamChannel getStream(@NonNull Closeable closeable,
                                        @NonNull Connection conn,
                                        short priority)
            throws InterruptedException, ExecutionException, ClosedException {

        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        QuicChannel quicChannel = conn.channel();

        QuicStreamChannel stored = getStream(quicChannel);
        if (stored != null) {
            if (stored.isOpen() && stored.isWritable()) {
                return stored;
            } else {
                removeStream(quicChannel);
            }
        }

        CompletableFuture<QuicStreamChannel> ctrl = getStream(quicChannel, priority);


        while (!ctrl.isDone()) {
            if (closeable.isClosed()) {
                ctrl.cancel(true);
            }
        }

        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        QuicStreamChannel stream = ctrl.get();

        Objects.requireNonNull(stream);
        putStream(quicChannel, stream);
        return stream;
    }

    public QuicStreamChannel getStream(@NonNull QuicChannel quicChannel) {
        return bitSwaps.get(quicChannel);

    }

    public void putStream(@NonNull QuicChannel quicChannel,
                          @NonNull QuicStreamChannel streamChannel) {
        bitSwaps.put(quicChannel, streamChannel);
    }

    public void removeStream(@NonNull QuicChannel quicChannel) {
        bitSwaps.remove(quicChannel);
    }
}

