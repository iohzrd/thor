package io.ipfs;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

import io.LogUtils;
import io.core.Closeable;
import io.core.ClosedException;
import io.core.ConnectionFailure;
import io.core.ConnectionIssue;
import io.core.ProtocolIssue;
import io.dht.Routing;
import io.ipfs.bitswap.BitSwapMessage;
import io.ipfs.bitswap.BitSwapNetwork;
import io.ipfs.bitswap.BitSwapProtocol;
import io.ipfs.cid.Cid;
import io.libp2p.ConnectionManager;
import io.libp2p.HostBuilder;
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
    private final ConnectionManager connectionManager;


    private LiteHost(@NonNull Host host,
                     @NonNull ConnectionManager connectionManager,
                     @NonNull Routing routing) {
        this.host = host;
        this.connectionManager = connectionManager;
        this.routing = routing;
    }

    public static BitSwapNetwork NewLiteHost(@NonNull Host host,
                                             @NonNull ConnectionManager connectionManager,
                                             @NonNull Routing routing) {
        return new LiteHost(host, connectionManager, routing);
    }

    @Override
    public boolean ConnectTo(@NonNull Closeable closeable, @NonNull PeerId peerId, boolean protect)
            throws ClosedException, ConnectionIssue {

        if (protect) {
            connectionManager.protectPeer(peerId);
        }

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
            throws ClosedException, ProtocolIssue, ConnectionFailure, ConnectionIssue {

        Connection con = HostBuilder.connect(closeable, host, peer);
        try {

            Object object = HostBuilder.stream(closeable, host, IPFS.ProtocolBitswap, con);

            BitSwapProtocol.BitSwapController controller = (BitSwapProtocol.BitSwapController) object;
            controller.sendRequest(message);

        } catch (ClosedException exception) {
            throw exception;
        } catch (Throwable throwable) {
            if (closeable.isClosed()) {
                throw new ClosedException();
            }
            Throwable cause = throwable.getCause();
            if (cause != null) {
                LogUtils.error(TAG, cause.getClass().getSimpleName());
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
                    throw new ConnectionFailure();
                }
                if (cause instanceof ReadTimeoutException) {
                    throw new ConnectionFailure();
                }
            }
            throw new RuntimeException(throwable);
        }

    }


    @Override
    public void FindProviders(@NonNull Closeable closeable, @NonNull Routing.Providers providers,
                              @NonNull Cid cid) throws ClosedException {
        LogUtils.error(TAG, "Find Start Content Provider " + cid.String());
        routing.FindProviders(closeable, providers, cid);
        LogUtils.error(TAG, "Find End Content Provider " + cid.String());

    }
}
