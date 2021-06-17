package io.ipfs.dht;

import androidx.annotation.NonNull;

import net.luminis.quic.stream.QuicStream;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import dht.pb.Dht;
import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.core.ProtocolIssue;
import io.ipfs.host.Connection;
import io.ipfs.host.ConnectionChannelHandler;
import io.ipfs.utils.DataHandler;

public class KadDhtSend extends ConnectionChannelHandler {

    private static final String TAG = KadDhtSend.class.getSimpleName();

    @NonNull
    private final DataHandler reader = new DataHandler(IPFS.PROTOCOL_READER_LIMIT);

    @NonNull
    private final CompletableFuture<Void> stream;

    public KadDhtSend(@NonNull Connection connection,
                      @NonNull QuicStream quicStream,
                      @NonNull CompletableFuture<Void> stream) {
        super(connection, quicStream);
        this.stream = stream;
    }

    public void exceptionCaught(@NonNull Connection connection, @NonNull Throwable cause) {
        LogUtils.debug(TAG, "" + cause);
        stream.completeExceptionally(cause);
        closeInputStream();
    }

    public void channelRead0(@NonNull Connection connection, @NonNull byte[] msg)
            throws Exception {

        reader.load(msg);

        if (reader.isDone()) {
            for (String received : reader.getTokens()) {
                if (Objects.equals(received, IPFS.DHT_PROTOCOL)) {
                    stream.complete(null);
                } else if (!Objects.equals(received, IPFS.STREAM_PROTOCOL)) {
                    throw new ProtocolIssue();
                }
            }
        } else {
            LogUtils.debug(TAG, "iteration " + msg.length + " "
                    + reader.expectedBytes() + " "
                    + connection.remoteAddress());
        }
    }
}
