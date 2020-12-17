package threads.thor.bt.net;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import threads.LogUtils;
import threads.thor.bt.Config;
import threads.thor.bt.CountingThreadFactory;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.service.RuntimeLifecycleBinder;

public class ConnectionSource implements IConnectionSource {

    private static final String TAG = ConnectionSource.class.getSimpleName();
    private final IPeerConnectionFactory connectionFactory;
    private final IPeerConnectionPool connectionPool;
    private final ExecutorService connectionExecutor;
    private final Config config;

    private final Map<ConnectionKey, CompletableFuture<ConnectionResult>> pendingConnections;
    // TODO: weak map
    private final ConcurrentMap<Peer, Long> unreachablePeers;

    public ConnectionSource(Set<PeerConnectionAcceptor> connectionAcceptors,
                            IPeerConnectionFactory connectionFactory,
                            IPeerConnectionPool connectionPool,
                            RuntimeLifecycleBinder lifecycleBinder,
                            Config config) {

        this.connectionFactory = connectionFactory;
        this.connectionPool = connectionPool;
        this.config = config;

        this.connectionExecutor = Executors.newFixedThreadPool(
                config.getMaxPendingConnectionRequests(),
                CountingThreadFactory.daemonFactory("bt.net.pool.connection-worker"));
        lifecycleBinder.onShutdown("Shutdown connection workers", connectionExecutor::shutdownNow);

        this.pendingConnections = new ConcurrentHashMap<>();
        this.unreachablePeers = new ConcurrentHashMap<>();


        IncomingConnectionListener incomingListener =
                new IncomingConnectionListener(connectionAcceptors, connectionExecutor, connectionPool, config);
        lifecycleBinder.onStartup("Initialize incoming connection acceptors", incomingListener::startup);
        lifecycleBinder.onShutdown("Shutdown incoming connection acceptors", incomingListener::shutdown);

    }

    @Override
    public ConnectionResult getConnection(Peer peer, TorrentId torrentId) {
        try {
            return getConnectionAsync(peer, torrentId).get();
        } catch (InterruptedException e) {
            return ConnectionResult.failure("Interrupted while waiting for connection", e);
        } catch (ExecutionException e) {
            return ConnectionResult.failure("Failed to establish connection due to error", e);
        }
    }

    @Override
    public CompletableFuture<ConnectionResult> getConnectionAsync(Peer peer, TorrentId torrentId) {
        ConnectionKey key = new ConnectionKey(peer, peer.getPort(), torrentId);

        CompletableFuture<ConnectionResult> connection = getExistingOrPendingConnection(key);
        if (connection != null) {
            return connection;
        }

        Long bannedAt = unreachablePeers.get(peer);
        if (bannedAt != null) {
            if (System.currentTimeMillis() - bannedAt >= config.getUnreachablePeerBanDuration().toMillis()) {
                LogUtils.debug(TAG, "Removing temporary ban for unreachable peer");
                unreachablePeers.remove(peer);
            } else {

                return CompletableFuture.completedFuture(ConnectionResult.failure("Peer is unreachable"));
            }
        }

        if (connectionPool.size() >= config.getMaxPeerConnections()) {

            return CompletableFuture.completedFuture(ConnectionResult.failure("Connections limit exceeded"));
        }

        synchronized (pendingConnections) {
            connection = getExistingOrPendingConnection(key);
            if (connection != null) {
                return connection;
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
            return connection;
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
