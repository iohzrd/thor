
package threads.thor.bt.torrent;

import threads.thor.bt.net.ConnectionKey;

/**
 * @since 1.0
 */
public interface IPeerWorkerFactory {

    /**
     * Create a threads.torrent-aware peer worker for a given peer connection.
     *
     * @since 1.9
     */
    PeerWorker createPeerWorker(ConnectionKey connectionKey);
}
