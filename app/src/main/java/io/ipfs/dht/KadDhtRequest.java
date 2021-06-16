package io.ipfs.dht;

import androidx.annotation.NonNull;

import com.google.protobuf.MessageLite;

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

public class KadDhtRequest extends ConnectionChannelHandler {


    private static final String TAG = KadDhtRequest.class.getSimpleName();
    @NonNull
    private final CompletableFuture<Dht.Message> request;
    @NonNull
    private final CompletableFuture<Void> activation;
    @NonNull
    private final MessageLite messageLite;
    private long start = System.currentTimeMillis();
    private DataHandler reader = new DataHandler(IPFS.PROTOCOL_READER_LIMIT);

    public KadDhtRequest(@NonNull Connection connection,
                         @NonNull QuicStream quicStream,
                         @NonNull CompletableFuture<Void> activation,
                         @NonNull CompletableFuture<Dht.Message> request,
                         @NonNull MessageLite messageLite) {
        super(connection, quicStream);
        this.request = request;
        this.activation = activation;
        this.messageLite = messageLite;
    }

    public void exceptionCaught(@NonNull Connection connection, @NonNull Throwable cause) {
        LogUtils.debug(TAG, connection.remoteId().toString() + " " + cause);
        request.completeExceptionally(cause);
        activation.completeExceptionally(cause);
        connection.disconnect();
    }

    public void channelRead0(@NonNull Connection connection, @NonNull byte[] msg)
            throws Exception {

        reader.load(msg);

        if (reader.isDone()) {

            for (String token : reader.getTokens()) {
                LogUtils.debug(TAG, "request " + token);
                if (Objects.equals(token, IPFS.DHT_PROTOCOL)) {
                    start = System.currentTimeMillis();
                    writeAndFlush(DataHandler.encode(messageLite));
                    // TODO addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).get());
                    // closeOutputStream();
                    activation.complete(null);
                } else if (!Objects.equals(token, IPFS.STREAM_PROTOCOL)) {
                    throw new ProtocolIssue();
                }
            }

            byte[] message = reader.getMessage();

            if (message != null) {
                request.complete(Dht.Message.parseFrom(message));
                close();
                LogUtils.debug(TAG, "done " + (System.currentTimeMillis() - start) + " "
                        + connection.remoteAddress());
                return;
            }
            reader = new DataHandler(IPFS.MESSAGE_SIZE_MAX);
        } else {
            LogUtils.debug(TAG, "iteration " + reader.hasRead() + " "
                    + reader.expectedBytes() + " "
                    + connection.remoteAddress());
        }
    }
}
