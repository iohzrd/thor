package threads.thor.magnet.peerexchange;

import androidx.annotation.NonNull;

import threads.thor.magnet.net.Peer;

class PeerEvent implements Comparable<PeerEvent> {

    private final Type type;
    private final Peer peer;
    private final long instant;

    private PeerEvent(Type type, Peer peer) {

        this.type = type;
        this.peer = peer;

        instant = System.currentTimeMillis();
    }

    static PeerEvent added(Peer peer) {
        return new PeerEvent(Type.ADDED, peer);
    }

    static PeerEvent dropped(Peer peer) {
        return new PeerEvent(Type.DROPPED, peer);
    }

    long getInstant() {
        return instant;
    }

    @Override
    public int compareTo(PeerEvent o) {

        if (instant == o.getInstant()) {
            return 0;
        } else if (instant - o.getInstant() >= 0) {
            return 1;
        } else {
            return -1;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "PeerEvent {type=" + type + ", peer=" + peer + ", instant=" + instant + '}';
    }

    enum Type {ADDED, DROPPED}
}
