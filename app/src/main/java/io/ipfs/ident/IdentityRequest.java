package io.ipfs.ident;

import androidx.annotation.NonNull;

import net.luminis.quic.stream.QuicStream;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import identify.pb.IdentifyOuterClass;
import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.core.ProtocolIssue;
import io.ipfs.host.Connection;
import io.ipfs.host.ConnectionChannelHandler;
import io.ipfs.utils.DataHandler;

public class IdentityRequest extends ConnectionChannelHandler {
    private static final String TAG = IdentityRequest.class.getSimpleName();
    @NonNull
    private final CompletableFuture<IdentifyOuterClass.Identify> request;

    private DataHandler reader = new DataHandler(25000);

    public IdentityRequest(@NonNull Connection connection,
                           @NonNull QuicStream quicStream,
                           @NonNull CompletableFuture<IdentifyOuterClass.Identify> request) {
        super(connection, quicStream);
        this.request = request;

    }

    public void exceptionCaught(@NonNull Connection connection, @NonNull Throwable cause) {
        LogUtils.error(TAG, "" + cause);
        request.completeExceptionally(cause);
        connection.disconnect();
    }

    public void channelRead0(@NonNull Connection connection, @NonNull byte[] msg)
            throws Exception {

        reader.load(msg);

        if (reader.isDone()) {

            for (String token : reader.getTokens()) {
                LogUtils.verbose(TAG, "request " + token);
                if (Objects.equals(token, IPFS.IDENTITY_PROTOCOL)) {
                    LogUtils.debug(TAG, "request " + token);
                } else if (!Objects.equals(token, IPFS.STREAM_PROTOCOL)) {
                    throw new ProtocolIssue();
                }
            }

            byte[] message = reader.getMessage();

            if (message != null) {
                request.complete(IdentifyOuterClass.Identify.parseFrom(message));
                closeInputStream();
            }
            reader = new DataHandler(25000);
        } else {
            LogUtils.debug(TAG, "iteration " + reader.hasRead() + " "
                    + reader.expectedBytes() + " "
                    + connection.remoteAddress());
        }
    }
}
