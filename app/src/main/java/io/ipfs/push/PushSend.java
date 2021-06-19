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
    private final DataHandler reader = new DataHandler(IPFS.PROTOCOL_READER_LIMIT);

    @NonNull
    private final CompletableFuture<Void> request;

    public PushSend(@NonNull QuicStream quicStream, @NonNull CompletableFuture<Void> request) {
        super(quicStream);
        this.request = request;
        new Thread(this::reading).start();
    }


    public void exceptionCaught(@NonNull Throwable cause) {
        LogUtils.debug(TAG, "" + cause);
        request.completeExceptionally(cause);
        reader.clear();
    }

    public void channelRead0(@NonNull byte[] msg)
            throws Exception {

        reader.load(msg);

        if (reader.isDone()) {
            for (String received : reader.getTokens()) {
                if (Objects.equals(received, IPFS.PUSH_PROTOCOL)) {
                    request.complete(null);
                } else if (!Objects.equals(received, IPFS.STREAM_PROTOCOL)) {
                    throw new ProtocolIssue();
                }
            }
        } else {
            LogUtils.debug(TAG, "iteration " + msg.length + " " + reader.expectedBytes());
        }
    }
}
