package threads.thor.magnet.torrent;

import threads.thor.magnet.net.ConnectionKey;

public class PeerWorkerFactory {

    private final MessageRouter router;

    public PeerWorkerFactory(MessageRouter router) {
        this.router = router;
    }

    public PeerWorker createPeerWorker(ConnectionKey connectionKey) {
        return new RoutingPeerWorker(connectionKey, router);
    }
}
