package threads.thor.bt.torrent;

import threads.thor.bt.net.ConnectionKey;

public class PeerWorkerFactory implements IPeerWorkerFactory {

    private final MessageRouter router;

    public PeerWorkerFactory(MessageRouter router) {
        this.router = router;
    }

    @Override
    public PeerWorker createPeerWorker(ConnectionKey connectionKey) {
        return new RoutingPeerWorker(connectionKey, router);
    }
}
