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
    private long start = System.currentTimeMillis();
    private DataHandler reader = new DataHandler(IPFS.BLOCK_SIZE_LIMIT);

    public KadDhtRequest(@NonNull Connection connection,
                         @NonNull QuicStream quicStream,
                         @NonNull CompletableFuture<Dht.Message> request) {
        super(connection, quicStream);
        this.request = request;
    }

    public void exceptionCaught(@NonNull Connection connection, @NonNull Throwable cause) {
        LogUtils.error(TAG, "" + cause);
        request.completeExceptionally(cause);
        closeInputStream();
    }

    public void channelRead0(@NonNull Connection connection, @NonNull byte[] msg)
            throws Exception {

        reader.load(msg);

        if (reader.isDone()) {

            for (String token : reader.getTokens()) {
                LogUtils.debug(TAG, "request " + token);
                if (Objects.equals(token, IPFS.DHT_PROTOCOL)) {
                    start = System.currentTimeMillis();
                } else if (!Objects.equals(token, IPFS.STREAM_PROTOCOL)) {
                    throw new ProtocolIssue();
                }
            }

            byte[] message = reader.getMessage();

            if (message != null) {
                request.complete(Dht.Message.parseFrom(message));
                closeInputStream();
                LogUtils.debug(TAG, "done " + (System.currentTimeMillis() - start) + " "
                        + connection.remoteAddress());
                return;
            }

        } else {
            LogUtils.debug(TAG, "iteration " + reader.hasRead() + " "
                    + reader.expectedBytes() + " "
                    + connection.remoteAddress());
        }
    }
}
