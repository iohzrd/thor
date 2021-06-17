package io.ipfs.relay;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;

import net.luminis.quic.QuicClientConnection;
import net.luminis.quic.stream.QuicStream;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.host.Connection;
import io.ipfs.host.PeerId;
import io.ipfs.utils.DataHandler;
import relay.pb.Relay;

public class RelayService {

    public static final String TAG = RelayService.class.getSimpleName();


    public static boolean canHop(@NonNull Connection conn, int timeout) {

        try {
            Relay.CircuitRelay message = relay.pb.Relay.CircuitRelay.newBuilder()
                    .setType(relay.pb.Relay.CircuitRelay.Type.CAN_HOP)
                    .build();

            QuicClientConnection quicChannel = conn.channel();
            long time = System.currentTimeMillis();

            CompletableFuture<Relay.CircuitRelay> request = new CompletableFuture<>();


            QuicStream quicStream = quicChannel.createStream(true);
            RelayRequest relayRequest = new RelayRequest(conn, quicStream, request);

            // TODO quicStream.pipeline().addFirst(new ReadTimeoutHandler(timeout, TimeUnit.SECONDS));

            // TODO quicStream.updatePriority(new QuicStreamPriority(IPFS.PRIORITY_URGENT, false));


            relayRequest.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
            relayRequest.writeAndFlush(DataHandler.writeToken(IPFS.RELAY_PROTOCOL));
            relayRequest.writeAndFlush(DataHandler.encode(message));
            relayRequest.closeOutputStream();


            Relay.CircuitRelay msg = request.get(timeout, TimeUnit.SECONDS);

            // TODO quicStream.close();

            LogUtils.info(TAG, "Request took " + (System.currentTimeMillis() - time));
            Objects.requireNonNull(msg);

            return msg.getType() == relay.pb.Relay.CircuitRelay.Type.STATUS;

        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }


    @Nullable
    public static QuicStream getStream(@NonNull Connection conn, @NonNull PeerId self,
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

        QuicClientConnection quicClientConnection = conn.channel();
        long time = System.currentTimeMillis();

        CompletableFuture<Relay.CircuitRelay> request = new CompletableFuture<>();

        try {
            QuicStream quicStream = quicClientConnection.createStream(true);

            RelayRequest relayRequest = new RelayRequest(conn, quicStream, request);

            // TODO streamChannel.updatePriority(new QuicStreamPriority(IPFS.PRIORITY_URGENT, false));
            relayRequest.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
            relayRequest.writeAndFlush(DataHandler.writeToken(IPFS.RELAY_PROTOCOL));
            relayRequest.writeAndFlush(DataHandler.encode(message));
            relayRequest.closeOutputStream();

            Relay.CircuitRelay msg = request.get(timeout, TimeUnit.SECONDS);
            LogUtils.info(TAG, "Request took " + (System.currentTimeMillis() - time));
            Objects.requireNonNull(msg);


            if (msg.getType() != Relay.CircuitRelay.Type.STATUS) {
                relayRequest.closeInputStream();
                relayRequest.closeOutputStream();
                // TODO quicStream.close();
                return null;
            }

            if (msg.getCode() != Relay.CircuitRelay.Status.SUCCESS) {
                relayRequest.closeInputStream();
                relayRequest.closeOutputStream();
                // TODO quicStream.close();
                return null;
            }

            return quicStream;

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return null;
    }

}
