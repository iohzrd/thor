package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import net.luminis.quic.stream.QuicStream;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.core.ProtocolIssue;
import io.ipfs.host.Connection;
import io.ipfs.host.ConnectionChannelHandler;
import io.ipfs.utils.DataHandler;

public class BitSwapSend extends ConnectionChannelHandler {

    private static final String TAG = BitSwapSend.class.getSimpleName();

    @NonNull
    private final DataHandler reader = new DataHandler(IPFS.PROTOCOL_READER_LIMIT);

    @NonNull
    private final BitSwap bitSwap;

    @NonNull
    private final CompletableFuture<BitSwapSend> done;

    public BitSwapSend(@NonNull Connection connection,
                       @NonNull QuicStream quicStream,
                       @NonNull CompletableFuture<BitSwapSend> done,
                       @NonNull BitSwap bitSwap) {
        super(connection, quicStream);
        this.done = done;
        this.bitSwap = bitSwap;
    }

    /* TODO
    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        LogUtils.debug(TAG, "channelUnregistered ");

        QuicChannel quicChannel = (QuicChannel) ctx.channel().parent();

        bitSwap.removeStream(quicChannel);
    } */

    public void exceptionCaught(@NonNull Connection connection, @NonNull Throwable cause) {
        LogUtils.error(TAG, " " + cause);
        done.completeExceptionally(cause);
        connection.disconnect();
    }

    public void channelRead0(@NonNull Connection connection, @NonNull byte[] msg)
            throws Exception {

        reader.load(msg);

        if (reader.isDone()) {
            for (String received : reader.getTokens()) {
                if (Objects.equals(received, IPFS.BITSWAP_PROTOCOL)) {
                    done.complete(this);
                } else if (!Objects.equals(received, IPFS.STREAM_PROTOCOL)) {
                    LogUtils.error(TAG, "NOT SUPPORTED " + received);
                    throw new ProtocolIssue();
                }
            }
        } else {
            LogUtils.error(TAG, "iteration " + msg.length + " "
                    + reader.expectedBytes() + " " + connection.remoteAddress());
        }
    }
}
