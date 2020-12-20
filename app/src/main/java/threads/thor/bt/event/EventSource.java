package threads.thor.bt.event;

import java.util.function.Consumer;

public interface EventSource {

    /**
     * Fired, when a new peer has been discovered for some threads.torrent.
     *
     * @since 1.5
     */
    void onPeerDiscovered(Consumer<PeerDiscoveredEvent> listener);

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
    void onPeerDisconnected(Consumer<PeerDisconnectedEvent> listener);

    /**
     * Fired, when processing of some threads.torrent has begun.
     *
     * @since 1.5
     */
    void onTorrentStarted(Consumer<TorrentStartedEvent> listener);

    /**
     * Fired, when processing of some threads.torrent has finished.
     *
     * @since 1.5
     */
    void onTorrentStopped(Consumer<TorrentStoppedEvent> listener);


}
