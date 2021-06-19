package io.ipfs.relay;

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
import relay.pb.Relay;

public class RelayRequest extends ConnectionChannelHandler {
    private static final String TAG = RelayRequest.class.getSimpleName();
    @NonNull
    private final CompletableFuture<Relay.CircuitRelay> request;

    private final DataHandler reader = new DataHandler(4096);

    public RelayRequest(@NonNull Connection connection,
                        @NonNull QuicStream quicStream,
                        @NonNull CompletableFuture<relay.pb.Relay.CircuitRelay> request) {
        super(connection, quicStream);
        this.request = request;
        new Thread(this::reading).start();
    }


    public void exceptionCaught(@NonNull Connection connection, @NonNull Throwable cause) {
        LogUtils.debug(TAG, "" + cause);
        request.completeExceptionally(cause);
        reader.clear();
    }


    public void channelRead0(@NonNull Connection connection, @NonNull byte[] msg)
            throws Exception {

        reader.load(msg);

        if (reader.isDone()) {

            for (String token : reader.getTokens()) {
                LogUtils.verbose(TAG, "request " + token);
                if (Objects.equals(token, IPFS.RELAY_PROTOCOL)) {
                    LogUtils.debug(TAG, "request " + token);
                } else if (!Objects.equals(token, IPFS.STREAM_PROTOCOL)) {
                    throw new ProtocolIssue();
                }
            }

            byte[] message = reader.getMessage();

            if (message != null) {
                request.complete(Relay.CircuitRelay.parseFrom(message));
                closeInputStream();
            }
        } else {
            LogUtils.debug(TAG, "iteration " + reader.hasRead() + " "
                    + reader.expectedBytes() + " " +
                    connection.remoteAddress().toString());
        }
    }
}
