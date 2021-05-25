package io.ipfs.relay;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.host.Connection;
import io.ipfs.host.PeerId;
import io.ipfs.utils.DataHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamPriority;
import io.netty.incubator.codec.quic.QuicStreamType;
import relay.pb.Relay;

public class RelayService {

    public static final String TAG = RelayService.class.getSimpleName();


    public static boolean canHop(@NonNull Connection conn, int timeout) {

        try {
            Relay.CircuitRelay message = relay.pb.Relay.CircuitRelay.newBuilder()
                    .setType(relay.pb.Relay.CircuitRelay.Type.CAN_HOP)
                    .build();

            QuicChannel quicChannel = conn.channel();
            long time = System.currentTimeMillis();

            CompletableFuture<Relay.CircuitRelay> request = new CompletableFuture<>();
            CompletableFuture<Void> activation = new CompletableFuture<>();


            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new RelayRequest(activation, request, message)).sync().get();

            streamChannel.pipeline().addFirst(new ReadTimeoutHandler(timeout, TimeUnit.SECONDS));

            streamChannel.updatePriority(new QuicStreamPriority(IPFS.PRIORITY_URGENT, false));


            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.RELAY_PROTOCOL));

            activation.thenCompose(s -> request);

            Relay.CircuitRelay msg = request.get(timeout, TimeUnit.SECONDS);

            streamChannel.close().get();

            LogUtils.info(TAG, "Request took " + (System.currentTimeMillis() - time));
            Objects.requireNonNull(msg);

            return msg.getType() == relay.pb.Relay.CircuitRelay.Type.STATUS;

        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }


    @Nullable
    public static QuicStreamChannel getStream(@NonNull Connection conn, @NonNull PeerId self,
                                              @NonNull PeerId peerId, int timeout) {

        Relay.CircuitRelay.Peer src = Relay.CircuitRelay.Peer.newBuilder()
                .setId(ByteString.copyFrom(self.getBytes())).build();
        Relay.CircuitRelay.Peer dest = Relay.CircuitRelay.Peer.newBuilder()
                .setId(ByteString.copyFrom(peerId.getBytes())).build();

        Relay.CircuitRelay message = relay.pb.Relay.CircuitRelay.newBuilder()
                .setType(relay.pb.Relay.CircuitRelay.Type.HOP)
                .setSrcPeer(src)
                .setDstPeer(dest)
                .build();

        QuicChannel quicChannel = conn.channel();
        long time = System.currentTimeMillis();

        CompletableFuture<Relay.CircuitRelay> request = new CompletableFuture<>();
        CompletableFuture<Void> activation = new CompletableFuture<>();

        try {
            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new RelayRequest(activation, request, message)).sync().get();


            streamChannel.updatePriority(new QuicStreamPriority(IPFS.PRIORITY_URGENT, false));

            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.RELAY_PROTOCOL));

            activation.thenCompose(s -> request);

            Relay.CircuitRelay msg = request.get(timeout, TimeUnit.SECONDS);
            LogUtils.info(TAG, "Request took " + (System.currentTimeMillis() - time));
            Objects.requireNonNull(msg);


            if (msg.getType() != Relay.CircuitRelay.Type.STATUS) {
                streamChannel.close().get();
                return null;
            }

            if (msg.getCode() != Relay.CircuitRelay.Status.SUCCESS) {
                streamChannel.close().get();
                return null;
            }

            return streamChannel;


        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return null;
    }

}
