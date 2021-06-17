package io.ipfs.push;

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

public class PushSend extends ConnectionChannelHandler {

    private static final String TAG = PushSend.class.getSimpleName();

    @NonNull
    private final DataHandler reader = new DataHandler(1000);

    @NonNull
    private final CompletableFuture<Void> done;

    public PushSend(@NonNull Connection connection,
                    @NonNull QuicStream quicStream,
                    @NonNull CompletableFuture<Void> done) {
        super(connection, quicStream);
        this.done = done;
    }


    public void exceptionCaught(@NonNull Connection connection, @NonNull Throwable cause) {
        LogUtils.error(TAG, "" + cause);
        done.completeExceptionally(cause);
        close();
        connection.disconnect();
    }

    public void channelRead0(@NonNull Connection connection, @NonNull byte[] msg)
            throws Exception {

        reader.load(msg);

        if (reader.isDone()) {
            for (String received : reader.getTokens()) {
                if (Objects.equals(received, IPFS.PUSH_PROTOCOL)) {
                    done.complete(null);
                } else if (!Objects.equals(received, IPFS.STREAM_PROTOCOL)) {
                    throw new ProtocolIssue();
                }
            }
        } else {
            LogUtils.debug(TAG, "iteration " + msg.length + " "
                    + reader.expectedBytes() + " " + connection.remoteAddress());
        }
    }
}
