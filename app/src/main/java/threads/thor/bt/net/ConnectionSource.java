package threads.thor.bt.net;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import threads.LogUtils;
import threads.thor.Settings;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.service.RuntimeLifecycleBinder;

public class ConnectionSource {

    private static final String TAG = ConnectionSource.class.getSimpleName();
    private final PeerConnectionFactory connectionFactory;
    private final PeerConnectionPool connectionPool;
    private final ExecutorService connectionExecutor;


    private final Map<ConnectionKey, CompletableFuture<ConnectionResult>> pendingConnections;
    // TODO: weak map
    private final ConcurrentMap<Peer, Long> unreachablePeers;

    public ConnectionSource(Set<SocketChannelConnectionAcceptor> connectionAcceptors,
                            PeerConnectionFactory connectionFactory,
                            PeerConnectionPool connectionPool,
                            RuntimeLifecycleBinder lifecycleBinder) {

        this.connectionFactory = connectionFactory;
        this.connectionPool = connectionPool;

        this.connectionExecutor = Executors.newFixedThreadPool(Settings.maxPendingConnectionRequests);
        lifecycleBinder.onShutdown("Shutdown connection workers", connectionExecutor::shutdownNow);

        this.pendingConnections = new ConcurrentHashMap<>();
        this.unreachablePeers = new ConcurrentHashMap<>();


        IncomingConnectionListener incomingListener =
                new IncomingConnectionListener(connectionAcceptors, connectionExecutor, connectionPool);
        lifecycleBinder.onStartup("Initialize incoming connection acceptors", incomingListener::startup);
        lifecycleBinder.onShutdown("Shutdown incoming connection acceptors", incomingListener::shutdown);

    }


    public void getConnectionAsync(Peer peer, TorrentId torrentId) {
        ConnectionKey key = new ConnectionKey(peer, peer.getPort(), torrentId);

        CompletableFuture<ConnectionResult> connection = getExistingOrPendingConnection(key);
        if (connection != null) {
            return;
        }

        Long bannedAt = unreachablePeers.get(peer);
        if (bannedAt != null) {
            if (System.currentTimeMillis() - bannedAt >= Settings.unreachablePeerBanDuration.toMillis()) {
                LogUtils.debug(TAG, "Removing temporary ban for unreachable peer");
                unreachablePeers.remove(peer);
            } else {

                CompletableFuture.completedFuture(ConnectionResult.failure());
                return;
            }
        }

        if (connectionPool.size() >= Settings.maxPeerConnections) {

            CompletableFuture.completedFuture(ConnectionResult.failure());
            return;
        }

        synchronized (pendingConnections) {
            connection = getExistingOrPendingConnection(key);
            if (connection != null) {
                return;
            }

            connection = CompletableFuture.supplyAsync(() -> {
                try {
                    ConnectionResult connectionResult =
                            connectionFactory.createOutgoingConnection(peer, torrentId);
                    if (connectionResult.isSuccess()) {
                        PeerConnection established = connectionResult.getConnection();
                        PeerConnection added = connectionPool.addConnectionIfAbsent(established);
                        if (added != established) {
                            established.closeQuietly();
                        }
                        return ConnectionResult.success(added);
                    } else {
                        return connectionResult;
                    }
                } finally {
                    synchronized (pendingConnections) {
                        pendingConnections.remove(key);
                    }
                }
            }, connectionExecutor).whenComplete((acquiredConnection, throwable) -> {
                if (acquiredConnection == null || throwable != null) {
                    unreachablePeers.putIfAbsent(peer, System.currentTimeMillis());
                }
                if (throwable != null) {
                    LogUtils.error(TAG,
                            "Failed to establish outgoing connection to peer: ", throwable);
                }
            });

            pendingConnections.put(key, connection);
        }
    }

    private CompletableFuture<ConnectionResult> getExistingOrPendingConnection(ConnectionKey key) {
        PeerConnection existingConnection = connectionPool.getConnection(key);
        if (existingConnection != null) {
            return CompletableFuture.completedFuture(ConnectionResult.success(existingConnection));
        }

        return pendingConnections.get(key);
    }
}
