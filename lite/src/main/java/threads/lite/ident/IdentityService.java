package threads.lite.ident;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import net.luminis.quic.QuicClientConnection;
import net.luminis.quic.stream.QuicStream;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import identify.pb.IdentifyOuterClass;
import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.Multiaddr;
import threads.lite.cid.PeerId;
import threads.lite.core.Closeable;
import threads.lite.core.ClosedException;
import threads.lite.host.PeerInfo;
import threads.lite.utils.DataHandler;

public class IdentityService {
    public static final String TAG = IdentityService.class.getSimpleName();

    @NonNull
    public static PeerInfo getPeerInfo(@NonNull Closeable closeable,
                                       @NonNull PeerId peerId,
                                       @NonNull QuicClientConnection conn) throws ClosedException {

        try {
            IdentifyOuterClass.Identify identify = IdentityService.getIdentity(closeable, conn);
            Objects.requireNonNull(identify);

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
    public static IdentifyOuterClass.Identify getIdentity(@NonNull Closeable closeable,
                                                          @NonNull QuicClientConnection quicClientConnection) throws ClosedException {
        try {
            long time = System.currentTimeMillis();


            CompletableFuture<IdentifyOuterClass.Identify> request =
                    requestIdentity(quicClientConnection);

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
            @NonNull QuicClientConnection quicChannel) {

        CompletableFuture<IdentifyOuterClass.Identify> request = new CompletableFuture<>();

        try {
            QuicStream quicStream = quicChannel.createStream(true);
            IdentityRequest identityRequest = new IdentityRequest(quicStream, request);

            // TODO quicStream.pipeline().addFirst(new ReadTimeoutHandler(10, TimeUnit.SECONDS));

            // TODO quicStream.updatePriority(new QuicStreamPriority(IPFS.PRIORITY_HIGH, false));

            identityRequest.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
            identityRequest.writeAndFlush(DataHandler.writeToken(IPFS.IDENTITY_PROTOCOL));
            identityRequest.closeOutputStream();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);

            request.completeExceptionally(throwable);
        }

        return request;
    }
}
