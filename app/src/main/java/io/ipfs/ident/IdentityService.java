package io.ipfs.ident;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import identify.pb.IdentifyOuterClass;
import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.host.Connection;
import io.ipfs.host.PeerId;
import io.ipfs.host.PeerInfo;
import io.ipfs.multiaddr.Multiaddr;
import io.ipfs.utils.DataHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamPriority;
import io.netty.incubator.codec.quic.QuicStreamType;

public class IdentityService {
    public static final String TAG = IdentityService.class.getSimpleName();

    @NonNull
    public static PeerInfo getPeerInfo(@NonNull Closeable closeable,
                                       @NonNull Connection conn) throws ClosedException {

        try {
            IdentifyOuterClass.Identify identify = IdentityService.getIdentity(closeable, conn);
            Objects.requireNonNull(identify);

            PeerId peerId = conn.remoteId();
            String agent = identify.getAgentVersion();
            String version = identify.getProtocolVersion();
            Multiaddr observedAddr = null;
            if (identify.hasObservedAddr()) {
                observedAddr = new Multiaddr(identify.getObservedAddr().toByteArray());
            }

            List<String> protocols = new ArrayList<>();
            List<Multiaddr> addresses = new ArrayList<>();
            List<ByteString> entries = identify.getProtocolsList().asByteStringList();
            for (ByteString entry : entries) {
                protocols.add(entry.toStringUtf8());
            }
            entries = identify.getListenAddrsList();
            for (ByteString entry : entries) {
                addresses.add(new Multiaddr(entry.toByteArray()));
            }

            return new PeerInfo(peerId, agent, version, addresses, protocols, observedAddr);
        } catch (ClosedException closedException) {
            throw closedException;
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    @NonNull
    public static IdentifyOuterClass.Identify getIdentity(
            @NonNull Closeable closeable, @NonNull Connection conn) throws ClosedException {
        try {
            QuicChannel quicChannel = conn.channel();
            long time = System.currentTimeMillis();


            CompletableFuture<IdentifyOuterClass.Identify> request = requestIdentity(quicChannel);

            while (!request.isDone()) {
                if (closeable.isClosed()) {
                    request.cancel(true);
                }
            }

            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            IdentifyOuterClass.Identify identify = request.get();
            LogUtils.info(TAG, "Request took " + (System.currentTimeMillis() - time));
            Objects.requireNonNull(identify);

            return identify;
        } catch (ClosedException closedException) {
            throw closedException;
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }


    private static CompletableFuture<IdentifyOuterClass.Identify> requestIdentity(
            @NonNull QuicChannel quicChannel) {

        CompletableFuture<IdentifyOuterClass.Identify> request = new CompletableFuture<>();
        CompletableFuture<Void> activation = new CompletableFuture<>();

        try {
            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new IdentityRequest(activation, request)).sync().get();


            streamChannel.pipeline().addFirst(new ReadTimeoutHandler(10, TimeUnit.SECONDS));

            streamChannel.updatePriority(new QuicStreamPriority(IPFS.PRIORITY_HIGH, false));


            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.IDENTITY_PROTOCOL));

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            activation.completeExceptionally(throwable);
            request.completeExceptionally(throwable);
        }

        return activation.thenCompose(s -> request);
    }
}
