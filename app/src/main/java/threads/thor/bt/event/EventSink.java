package threads.thor.bt.event;

import threads.thor.bt.data.Bitfield;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.ConnectionKey;
import threads.thor.bt.net.Peer;

public interface EventSink {

    /**
     * Generate event, that a new peer has been discovered for some threads.torrent.
     *
     * @since 1.5
     */
    void firePeerDiscovered(TorrentId torrentId, Peer peer);

    /**
     * Generate event, that a new connection with some peer has been established.
     *
     * @since 1.9
     */
    void firePeerConnected(ConnectionKey connectionKey);

    /**
     * Generate event, that a connection with some peer has been terminated.
     *
     * @since 1.9
     */
    void firePeerDisconnected(ConnectionKey connectionKey);

    /**
     * Generate event, that local information about some peer's data has been updated.
     *
     * @since 1.9
     */
    void firePeerBitfieldUpdated(TorrentId torrentId, ConnectionKey connectionKey, Bitfield bitfield);

    /**
     * Generate event, that processing of some threads.torrent has begun.
     *
     * @since 1.5
     */
    void fireTorrentStarted(TorrentId torrentId);

    /**
     * Generate event, that threads.torrent's metadata has been fetched.
     *
     * @since 1.9
     */
    void fireMetadataAvailable(TorrentId torrentId);

    /**
     * Generate event, that processing of some threads.torrent has finished.
     *
     * @since 1.5
     */
    void fireTorrentStopped(TorrentId torrentId);

    /**
     * Generate event, that the downloading and verification
     * of one of threads.torrent's pieces has been finished.
     *
     * @since 1.8
     */
    void firePieceVerified(TorrentId torrentId, int pieceIndex);
}
