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
    private final DataHandler reader = new DataHandler(1000);

    @NonNull
    private final CompletableFuture<Void> stream;
    @NonNull
    private final Dht.Message message;

    public KadDhtSend(@NonNull Connection connection,
                      @NonNull QuicStream quicStream,
                      @NonNull CompletableFuture<Void> stream,
                      @NonNull Dht.Message message) {
        super(connection, quicStream);
        this.stream = stream;
        this.message = message;
    }

    public void exceptionCaught(@NonNull Connection connection, @NonNull Throwable cause) {
        LogUtils.error(TAG, " " + cause);
        stream.completeExceptionally(cause);
        connection.disconnect();
    }

    public void channelRead0(@NonNull Connection connection, @NonNull byte[] msg)
            throws Exception {

        reader.load(msg);

        if (reader.isDone()) {
            for (String received : reader.getTokens()) {
                if (Objects.equals(received, IPFS.DHT_PROTOCOL)) {
                    writeAndFlush(DataHandler.encode(message));
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
