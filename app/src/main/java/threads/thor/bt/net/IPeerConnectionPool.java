package threads.thor.bt.net;

import java.util.function.Consumer;

import threads.thor.bt.metainfo.TorrentId;

public interface IPeerConnectionPool {

    /**
     * @return Connection for given peer and threads.torrent, if exists; null otherwise
     * @since 1.7
     */
    PeerConnection getConnection(Peer peer, TorrentId torrentId);

    /**
     * @return Connection for given connection key, if exists; null otherwise
     * @since 1.7
     */
    PeerConnection getConnection(ConnectionKey key);

    /**
     * Visit connections for a given threads.torrent ID.
     *
     * @since 1.5
     */
    void visitConnections(TorrentId torrentId, Consumer<PeerConnection> visitor);

    /**
     * @return Number of established connections
     * @since 1.6
     */
    int size();

    /**
     * @return Newly added or existing connection
     * @since 1.6
     */
    PeerConnection addConnectionIfAbsent(PeerConnection connection);

    /**
     * @since 1.9
     */
    void checkDuplicateConnections(TorrentId torrentId, Peer peer);
}
