package io.ipfs.relay;

import androidx.annotation.NonNull;

import com.google.protobuf.MessageLite;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.host.Connection;
import io.ipfs.utils.DataHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamPriority;
import io.netty.incubator.codec.quic.QuicStreamType;
import relay.pb.Relay;

public class RelayService {

    public static final String TAG = RelayService.class.getSimpleName();

    @NonNull
    public static Relay.CircuitRelay getRelay(
            @NonNull Closeable closeable, @NonNull Connection conn) throws ClosedException {

        try {
            Relay.CircuitRelay message = relay.pb.Relay.CircuitRelay.newBuilder()
                    .setType(relay.pb.Relay.CircuitRelay.Type.CAN_HOP)
                    .build();

            QuicChannel quicChannel = conn.channel();
            long time = System.currentTimeMillis();


            CompletableFuture<Relay.CircuitRelay> request = requestRelay(quicChannel, message
            );

            while (!request.isDone()) {
                if (closeable.isClosed()) {
                    request.cancel(true);
                }
            }

            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            Relay.CircuitRelay msg = request.get();
            LogUtils.info(TAG, "Request took " + (System.currentTimeMillis() - time));
            Objects.requireNonNull(msg);

            return msg;

        } catch (ClosedException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }


    private static CompletableFuture<Relay.CircuitRelay> requestRelay(
            @NonNull QuicChannel quicChannel, @NonNull MessageLite messageLite) {

        CompletableFuture<Relay.CircuitRelay> request = new CompletableFuture<>();
        CompletableFuture<Void> activation = new CompletableFuture<>();

        try {
            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new RelayRequest(activation, request, messageLite)).sync().get();


            //streamChannel.pipeline().addFirst(new ReadTimeoutHandler(5, TimeUnit.SECONDS));

            streamChannel.updatePriority(new QuicStreamPriority(IPFS.PRIORITY_URGENT, false));


            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.RELAY_PROTOCOL));

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            activation.completeExceptionally(throwable);
            request.completeExceptionally(throwable);
        }

        return activation.thenCompose(s -> request);
    }
}
