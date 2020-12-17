package threads.thor.bt.event;

import java.util.function.Consumer;

public interface EventSource {

    /**
     * Fired, when a new peer has been discovered for some threads.torrent.
     *
     * @since 1.5
     */
    EventSource onPeerDiscovered(Consumer<PeerDiscoveredEvent> listener);

    /**
     * Fired, when a new connection with some peer has been established.
     *
     * @since 1.5
     */
    EventSource onPeerConnected(Consumer<PeerConnectedEvent> listener);

    /**
     * Fired, when a connection with some peer has been terminated.
     *
     * @since 1.5
     */
    EventSource onPeerDisconnected(Consumer<PeerDisconnectedEvent> listener);

    /**
     * Fired, when local information about some peer's data has been updated.
     *
     * @since 1.5
     */
    EventSource onPeerBitfieldUpdated(Consumer<PeerBitfieldUpdatedEvent> listener);

    /**
     * Fired, when processing of some threads.torrent has begun.
     *
     * @since 1.5
     */
    EventSource onTorrentStarted(Consumer<TorrentStartedEvent> listener);

    /**
     * Fired, when threads.torrent's metadata has been fetched.
     *
     * @since 1.9
     */
    EventSource onMetadataAvailable(Consumer<MetadataAvailableEvent> listener);

    /**
     * Fired, when processing of some threads.torrent has finished.
     *
     * @since 1.5
     */
    EventSource onTorrentStopped(Consumer<TorrentStoppedEvent> listener);

    /**
     * Fired, when downloading and verification of one of threads.torrent's pieces has been finished.
     *
     * @since 1.8
     */
    EventSource onPieceVerified(Consumer<PieceVerifiedEvent> listener);
}
