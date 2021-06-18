package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import net.luminis.quic.stream.QuicStream;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import bitswap.pb.MessageOuterClass;
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
    private final CompletableFuture<BitSwapSend> done;

    public BitSwapSend(@NonNull Connection connection,
                       @NonNull QuicStream quicStream,
                       @NonNull CompletableFuture<BitSwapSend> done) {
        super(connection, quicStream);
        this.done = done;
    }


    public void exceptionCaught(@NonNull Connection connection, @NonNull Throwable cause) {
        LogUtils.debug(TAG, "" + cause);
        done.completeExceptionally(cause);
        reader.clear();
    }

    public void channelRead0(@NonNull Connection connection, @NonNull byte[] msg)
            throws Exception {

        reader.load(msg);

        if (reader.isDone()) {
            for (String received : reader.getTokens()) {
                if (Objects.equals(received, IPFS.BITSWAP_PROTOCOL)) {
                    done.complete(this);
                } else if (!Objects.equals(received, IPFS.STREAM_PROTOCOL)) {
                    LogUtils.debug(TAG, "NOT SUPPORTED " + received);
                    throw new ProtocolIssue();
                }
            }
        } else {
            LogUtils.error(TAG, "iteration "  + reader.hasRead() + " "
                + reader.expectedBytes() + " " + connection.remoteAddress()
                + " StreamId " + streamId + " PeerId " + connection.remoteId());
        }
    }
}
