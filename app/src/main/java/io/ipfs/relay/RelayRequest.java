package io.ipfs.relay;

import androidx.annotation.NonNull;

import com.google.protobuf.MessageLite;

import net.luminis.quic.stream.QuicStream;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.core.ProtocolIssue;
import io.ipfs.host.Connection;
import io.ipfs.host.ConnectionChannelHandler;
import io.ipfs.utils.DataHandler;
import relay.pb.Relay;

public class RelayRequest extends ConnectionChannelHandler {
    private static final String TAG = RelayRequest.class.getSimpleName();
    @NonNull
    private final CompletableFuture<Relay.CircuitRelay> request;
    @NonNull
    private final CompletableFuture<Void> activation;
    @NonNull
    private final MessageLite messageLite;
    private DataHandler reader = new DataHandler(4096);

    public RelayRequest(@NonNull Connection connection,
                        @NonNull QuicStream quicStream,
                        @NonNull CompletableFuture<Void> activation,
                        @NonNull CompletableFuture<relay.pb.Relay.CircuitRelay> request,
                        @NonNull MessageLite messageLite) {
        super(connection, quicStream);
        this.request = request;
        this.activation = activation;
        this.messageLite = messageLite;
    }


    public void exceptionCaught(@NonNull Connection connection, @NonNull Throwable cause) {
        LogUtils.error(TAG, " " + cause);
        request.completeExceptionally(cause);
        activation.completeExceptionally(cause);
        connection.disconnect();
    }


    public void channelRead0(@NonNull Connection connection, @NonNull byte[] msg)
            throws Exception {

        reader.load(msg);

        if (reader.isDone()) {

            for (String token : reader.getTokens()) {
                LogUtils.error(TAG, "request " + token);
                if (Objects.equals(token, IPFS.RELAY_PROTOCOL)) {
                    writeAndFlush(DataHandler.encode(messageLite));
                    activation.complete(null);
                } else if (!Objects.equals(token, IPFS.STREAM_PROTOCOL)) {
                    throw new ProtocolIssue();
                }
            }

            byte[] message = reader.getMessage();

            if (message != null) {
                request.complete(Relay.CircuitRelay.parseFrom(message));
            }
            reader = new DataHandler(4096);
        } else {
            LogUtils.debug(TAG, "iteration " + reader.hasRead() + " "
                    + reader.expectedBytes() + " " +
                    connection.remoteAddress().toString());
        }
    }
}
