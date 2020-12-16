package threads.thor.bt.peerexchange;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import threads.thor.bt.net.Peer;
import threads.thor.bt.peer.PeerSource;

class PeerExchangePeerSource implements PeerSource {

    private final Object lock;
    private final Queue<PeerExchange> messages;
    private volatile Collection<Peer> peers;
    private volatile boolean hasNewPeers;

    PeerExchangePeerSource() {
        messages = new LinkedBlockingQueue<>();
        peers = Collections.emptyList();
        lock = new Object();
    }

    @Override
    public boolean update() {

        if (!hasNewPeers) {
            return false;
        }

        synchronized (lock) {
            peers = collectPeers(messages);
            hasNewPeers = false;
        }
        return true;
    }

    private Collection<Peer> collectPeers(Collection<PeerExchange> messages) {
        Set<Peer> peers = new HashSet<>();
        messages.forEach(message -> {
            peers.addAll(message.getAdded());
            message.getDropped().forEach(peers::remove);
        });
        return peers;
    }

    void addMessage(PeerExchange message) {
        synchronized (lock) {
            messages.add(message);
            // according to BEP-11 the same peers can't be dropped in the same message,
            // so it's sufficient to check if list of added peers is not empty
            hasNewPeers = hasNewPeers || !message.getAdded().isEmpty();
        }
    }

    @Override
    public Collection<Peer> getPeers() {
        return peers;
    }
}
