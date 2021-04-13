package io.dht;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import io.libp2p.core.PeerId;

public class PeerDistanceSorter extends ArrayList<PeerDistanceSorter.PeerDistance> {
    private final ID target;

    public PeerDistanceSorter(@NonNull ID target) {
        this.target = target;
    }

    private void appendPeer(@NonNull PeerId peerId, @NonNull ID id) {
        this.add(new PeerDistance(peerId, Util.xor(target, id)));
    }

    public void appendPeersFromList(@NonNull Bucket bucket) {
        for (Bucket.PeerInfo peerInfo : bucket) {
            appendPeer(peerInfo.getPeerId(), peerInfo.getID());
        }
    }

    public static class PeerDistance implements Comparable<PeerDistance> {
        private final PeerId peerId;
        private final ID distance;

        public PeerDistance(@NonNull PeerId peerId, @NonNull ID distance) {
            this.peerId = peerId;
            this.distance = distance;
        }

        @Override
        public int compareTo(@NonNull PeerDistance o) {
            return this.distance.compareTo(o.distance);
        }

        @NonNull
        public PeerId getPeerId() {
            return peerId;
        }
    }
}
