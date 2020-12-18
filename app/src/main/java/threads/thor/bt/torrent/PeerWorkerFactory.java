package threads.thor.bt.torrent;

import threads.thor.bt.net.ConnectionKey;

public class PeerWorkerFactory {

    private final MessageRouter router;

    public PeerWorkerFactory(MessageRouter router) {
        this.router = router;
    }

    public PeerWorker createPeerWorker(ConnectionKey connectionKey) {
        return new RoutingPeerWorker(connectionKey, router);
    }
}
