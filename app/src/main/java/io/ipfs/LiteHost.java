package io.ipfs;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

import io.core.Closeable;
import io.core.ClosedException;
import io.core.ConnectionIssue;
import io.core.ProtocolIssue;
import io.core.TimeoutIssue;
import io.dht.Routing;
import io.ipfs.bitswap.BitSwapMessage;
import io.ipfs.bitswap.BitSwapNetwork;
import io.ipfs.bitswap.BitSwapProtocol;
import io.ipfs.cid.Cid;
import io.libp2p.HostBuilder;
import io.libp2p.Metrics;
import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionClosedException;
import io.libp2p.core.Host;
import io.libp2p.core.NoSuchRemoteProtocolException;
import io.libp2p.core.PeerId;
import io.libp2p.etc.types.NonCompleteException;
import io.libp2p.etc.types.NothingToCompleteException;
import io.netty.handler.timeout.ReadTimeoutException;


public class LiteHost implements BitSwapNetwork {
    private static final String TAG = LiteHost.class.getSimpleName();
    @NonNull
    private final Host host;
    @NonNull
    private final Routing routing;
    @NonNull
    private final Metrics metrics;


    private LiteHost(@NonNull Host host, @NonNull Metrics metrics,
                     @NonNull Routing routing) {
        this.host = host;
        this.metrics = metrics;
        this.routing = routing;
    }

    public static BitSwapNetwork create(@NonNull Host host, @NonNull Metrics metrics,
                                        @NonNull Routing routing) {
        return new LiteHost(host, metrics, routing);
    }

    @Override
    public boolean ConnectTo(@NonNull Closeable closeable, @NonNull PeerId peerId)
            throws ClosedException, ConnectionIssue {

        return HostBuilder.connect(closeable, host, peerId) != null;
    }


    @NonNull
    @Override
    public Set<PeerId> getPeers() {
        Set<PeerId> peerIds = new HashSet<>();
        for (Connection connection : host.getNetwork().getConnections()) {
            peerIds.add(connection.secureSession().getRemoteId());
        }

        return peerIds;
    }

    @Override
    public PeerId Self() {
        return host.getPeerId();
    }


    @Override
    public void WriteMessage(@NonNull Closeable closeable, @NonNull PeerId peer,
                             @NonNull BitSwapMessage message)
            throws ClosedException, ProtocolIssue, TimeoutIssue, ConnectionIssue {

        Connection con = HostBuilder.connect(closeable, host, peer);
        try {
            synchronized (peer.toBase58().intern()) {

                if (closeable.isClosed()) {
                    throw new ClosedException();
                }

                metrics.active(peer);
                Object object = HostBuilder.stream(closeable, host, IPFS.ProtocolBitswap, con);

                BitSwapProtocol.BitSwapController controller = (BitSwapProtocol.BitSwapController) object;
                controller.sendRequest(message);
            }
        } catch (ClosedException exception) {
            throw exception;
        } catch (Throwable throwable) {

            Throwable cause = throwable.getCause();
            if (cause != null) {
                if (cause instanceof NoSuchRemoteProtocolException) {
                    throw new ProtocolIssue();
                }
                if (cause instanceof NothingToCompleteException) {
                    throw new ConnectionIssue();
                }
                if (cause instanceof NonCompleteException) {
                    throw new ConnectionIssue();
                }
                if (cause instanceof ConnectionClosedException) {
                    throw new ConnectionIssue();
                }
                if (cause instanceof ReadTimeoutException) {
                    throw new TimeoutIssue();
                }
            }
            throw new RuntimeException(throwable);
        } finally {
            metrics.done(peer);
        }

    }


    @Override
    public void FindProviders(@NonNull Closeable closeable, @NonNull Routing.Providers providers,
                              @NonNull Cid cid) throws ClosedException {
        routing.FindProviders(closeable, providers, cid);
    }
}
