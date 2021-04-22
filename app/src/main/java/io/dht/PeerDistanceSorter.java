package io.dht;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.LogUtils;
import io.libp2p.core.PeerId;

public class PeerDistanceSorter extends ArrayList<PeerDistanceSorter.PeerDistance> {
    private static final String TAG = PeerDistanceSorter.class.getSimpleName();
    private final ID target;

    public PeerDistanceSorter(@NonNull ID target) {
        this.target = target;
    }

    @NotNull
    @Override
    public String toString() {
        return "PeerDistanceSorter{" +
                "target=" + target +
                '}';
    }

    public void appendPeer(@NonNull PeerId peerId, @NonNull ID id) {
        this.add(new PeerDistance(peerId, Util.xor(target, id)));
    }

    public void appendPeersFromList(@NonNull Bucket bucket) {
        for (Bucket.PeerInfo peerInfo : bucket.elements()) {
            appendPeer(peerInfo.getPeerId(), peerInfo.getID());
        }
    }

    public List<PeerId> sortedList() {
        LogUtils.verbose(TAG, this.toString());
        Collections.sort(this);
        List<PeerId> list = new ArrayList<>();
        for (PeerDistanceSorter.PeerDistance dist : this) {
            list.add(dist.peerId);
        }
        return list;
    }

    public static class PeerDistance implements Comparable<PeerDistance> {
        private final PeerId peerId;
        private final ID distance;

        protected PeerDistance(@NonNull PeerId peerId, @NonNull ID distance) {
            this.peerId = peerId;
            this.distance = distance;
        }

        @NotNull
        @Override
        public String toString() {
            return "PeerDistance{" +
                    "peerId=" + peerId +
                    ", distance=" + distance +
                    '}';
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
