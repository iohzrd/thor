package threads.thor.bt.net;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import threads.LogUtils;
import threads.thor.bt.Config;
import threads.thor.bt.CountingThreadFactory;
import threads.thor.bt.event.EventSink;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.service.RuntimeLifecycleBinder;

public class PeerConnectionPool implements IPeerConnectionPool {
    private static final String TAG = PeerConnectionPool.class.getSimpleName();
    private final Config config;
    private final EventSink eventSink;
    private final ScheduledExecutorService cleaner;
    private final Connections connections;
    private final ReentrantLock connectionLock;
    private final Duration peerConnectionInactivityThreshold;

    public PeerConnectionPool(
            EventSink eventSink,
            RuntimeLifecycleBinder lifecycleBinder,
            Config config) {

        this.config = config;
        this.eventSink = eventSink;
        this.peerConnectionInactivityThreshold = config.getPeerConnectionInactivityThreshold();
        this.connections = new Connections();
        this.connectionLock = new ReentrantLock();

        this.cleaner = Executors.newScheduledThreadPool(1, r -> new Thread(r, "bt.net.pool.cleaner"));
        lifecycleBinder.onStartup("Schedule periodic cleanup of stale peer connections",
                () -> cleaner.scheduleAtFixedRate(new Cleaner(), 1, 1, TimeUnit.SECONDS));

        ExecutorService executor = Executors.newFixedThreadPool(
                config.getMaxPendingConnectionRequests(),
                CountingThreadFactory.daemonFactory("bt.net.pool.connection-worker"));

        lifecycleBinder.onShutdown("Shutdown outgoing connection request processor", executor::shutdownNow);
        lifecycleBinder.onShutdown("Shutdown connection pool", this::shutdown);
    }

    @Override
    public PeerConnection getConnection(Peer peer, TorrentId torrentId) {
        return connections.get(peer, peer.getPort(), torrentId).orElse(null);
    }

    @Override
    public PeerConnection getConnection(ConnectionKey key) {
        return connections.get(key).orElse(null);
    }

    @Override
    public void visitConnections(TorrentId torrentId, Consumer<PeerConnection> visitor) {
        connections.visitConnections(torrentId, visitor);
    }

    @Override
    public int size() {
        return connections.count();
    }

    @Override
    public PeerConnection addConnectionIfAbsent(PeerConnection newConnection) {
        PeerConnection existingConnection = null;

        ConnectionKey connectionKey = new ConnectionKey(newConnection.getRemotePeer(),
                newConnection.getRemotePort(), newConnection.getTorrentId());

        connectionLock.lock();
        try {
            if (connections.count() >= config.getMaxPeerConnections()) {

                newConnection.closeQuietly();
            } else {
                List<PeerConnection> connectionsWithSameAddress =
                        getConnectionsForAddress(newConnection.getTorrentId(), newConnection.getRemotePeer());

                for (PeerConnection connection : connectionsWithSameAddress) {
                    if (!connection.getRemotePeer().isPortUnknown()
                            && connection.getRemotePeer().getPort() == newConnection.getRemotePeer().getPort()) {
                        existingConnection = connection;
                        break;
                    }
                }
                if (existingConnection == null) {
                    if (connections.putIfAbsent(connectionKey, newConnection) != null) {
                        throw new IllegalStateException();
                    }
                }
            }
        } finally {
            connectionLock.unlock();
        }
        if (existingConnection != null) {

            return existingConnection;
        } else {
            eventSink.firePeerConnected(connectionKey);
            return newConnection;
        }
    }

    @Override
    public void checkDuplicateConnections(TorrentId torrentId, Peer peer) {
        connectionLock.lock();
        try {
            List<PeerConnection> connectionsWithSameAddress = getConnectionsForAddress(torrentId, peer);

            PeerConnection outgoingConnection = null;
            PeerConnection incomingConnection = null;
            for (PeerConnection connection : connectionsWithSameAddress) {
                if (connection.getRemotePort() == peer.getPort()) {
                    outgoingConnection = connection;
                } else if (connection.getRemotePeer().getPort() == peer.getPort()) {
                    incomingConnection = connection;
                }
                if (outgoingConnection != null && incomingConnection != null) {
                    break;
                }
            }
            if (outgoingConnection != null && incomingConnection != null) {
                // always prefer to keep outgoing connections

                incomingConnection.closeQuietly();
            }
        } finally {
            connectionLock.unlock();
        }
    }

    private List<PeerConnection> getConnectionsForAddress(TorrentId torrentId, Peer peer) {
        List<PeerConnection> connectionsWithSameAddress = new ArrayList<>();
        connections.visitConnections(torrentId, connection -> {
            Peer connectionPeer = connection.getRemotePeer();
            if (connectionPeer.getInetAddress().equals(peer.getInetAddress())) {
                connectionsWithSameAddress.add(connection);
            }
        });
        return connectionsWithSameAddress;
    }

    private void purgeConnection(PeerConnection connection) {
        ConnectionKey connectionKey = new ConnectionKey(connection.getRemotePeer(),
                connection.getRemotePort(), connection.getTorrentId());
        connections.remove(connectionKey, connection);
        connection.closeQuietly();
        eventSink.firePeerDisconnected(connectionKey);
    }

    private void shutdown() {
        shutdownCleaner();
        connections.visitConnections(PeerConnection::closeQuietly);
    }

    private void shutdownCleaner() {
        cleaner.shutdown();
        try {
            cleaner.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LogUtils.error(TAG, "Interrupted while waiting for the cleaner's shutdown");
        }
        if (!cleaner.isShutdown()) {
            cleaner.shutdownNow();
        }
    }

    private class Cleaner implements Runnable {
        @Override
        public void run() {
            if (connections.count() == 0) {
                return;
            }

            connectionLock.lock();
            try {
                connections.visitConnections(connection -> {
                    if (connection.isClosed()) {
                        purgeConnection(connection);

                    } else if (System.currentTimeMillis() - connection.getLastActive()
                            >= peerConnectionInactivityThreshold.toMillis()) {

                        purgeConnection(connection);
                    }
                    // can send keep-alives here based on lastActiveTime
                });

            } finally {
                connectionLock.unlock();
            }
        }
    }
}

class Connections {
    private final ConcurrentMap<ConnectionKey, PeerConnection> connections;
    private final ConcurrentMap<TorrentId, Collection<PeerConnection>> connectionsByTorrent;

    Connections() {
        this.connections = new ConcurrentHashMap<>();
        this.connectionsByTorrent = new ConcurrentHashMap<>();
    }

    int count() {
        return connections.size();
    }

    synchronized boolean remove(ConnectionKey key, PeerConnection connection) {
        Objects.requireNonNull(connection);

        PeerConnection removed = connections.remove(key);
        boolean success = (removed == connection);
        if (success) {
            Collection<PeerConnection> torrentConnections = connectionsByTorrent.get(key.getTorrentId());
            torrentConnections.remove(removed);
            if (torrentConnections.isEmpty()) {
                connectionsByTorrent.remove(key.getTorrentId());
            }
        }
        return success;
    }

    synchronized PeerConnection putIfAbsent(ConnectionKey key, PeerConnection connection) {
        Objects.requireNonNull(connection);

        PeerConnection existing = connections.putIfAbsent(key, connection);
        if (existing == null) {
            connectionsByTorrent.computeIfAbsent(key.getTorrentId(), id -> ConcurrentHashMap.newKeySet())
                    .add(connection);
        }
        return existing;
    }

    Optional<PeerConnection> get(Peer peer, int remotePort, TorrentId torrentId) {
        return get(new ConnectionKey(peer, remotePort, torrentId));
    }

    Optional<PeerConnection> get(ConnectionKey key) {
        return Optional.ofNullable(connections.get(key));
    }

    void visitConnections(Consumer<PeerConnection> visitor) {
        connections.values().forEach(visitor::accept);
    }

    void visitConnections(TorrentId torrentId, Consumer<PeerConnection> visitor) {
        Collection<PeerConnection> connections = connectionsByTorrent.get(torrentId);
        if (connections != null) {
            connections.forEach(visitor::accept);
        }
    }
}
