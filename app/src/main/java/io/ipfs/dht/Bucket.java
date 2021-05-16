package io.ipfs.dht;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.LogUtils;
import io.ipfs.host.PeerId;

public class Bucket {

    private final ConcurrentHashMap<PeerId, PeerInfo> peers = new ConcurrentHashMap<>();

    @Nullable
    public PeerInfo getPeer(@NonNull PeerId p) {
        return peers.get(p);
    }

    @Nullable
    public PeerInfo weakest(@NonNull MinFunc func) {
        if (size() == 0) {
            return null;
        }
        PeerInfo minVal = null;
        for (Map.Entry<PeerId, PeerInfo> entry : peers.entrySet()) {
            if (minVal == null) {
                minVal = entry.getValue();
            } else {
                if (func.less(minVal, entry.getValue())) {
                    minVal = entry.getValue();
                }
            }
        }
        return minVal;
    }

    public synchronized Bucket split(int cpl, @NonNull ID target) {

        Bucket newbie = new Bucket();

        for (PeerInfo e : elements()) {
            ID pDhtId = e.getID();
            int peerCPL = Util.CommonPrefixLen(pDhtId, target);
            if (peerCPL > cpl) {
                newbie.pushFront(e);
                peers.remove(e.getPeerId());
            }
        }
        return newbie;
    }

    @NonNull
    @Override
    public String toString() {
        return "Bucket{" +
                "peers=" + peers.size() +
                '}';
    }

    public void addPeer(@NonNull PeerId p, long latency, boolean isReplaceable) {
        Bucket.PeerInfo peerInfo = new Bucket.PeerInfo(p, latency, isReplaceable);

        if (LogUtils.isDebug()) {
            if (peers.containsKey(p)) {
                throw new RuntimeException("invalid state");
            }
        }
        peers.put(p, peerInfo);
    }

    public boolean removePeer(@NonNull PeerId p) {
        return peers.remove(p) != null;
    }

    public int size() {
        return peers.size();
    }

    private void pushFront(@NonNull PeerInfo peerInfo) {
        if (LogUtils.isDebug()) {
            if (peers.containsKey(peerInfo.getPeerId())) {
                throw new RuntimeException("invalid state");
            }
        }
        peers.put(peerInfo.getPeerId(), peerInfo);
    }

    @NonNull
    public Collection<PeerInfo> elements() {
        return peers.values();
    }

    public interface MinFunc {
        boolean less(@NonNull PeerInfo p1, @NonNull PeerInfo p2);
    }

    public static class PeerInfo {
        @NonNull
        private final PeerId peerId;
        @NonNull
        private final ID id;
        // if a bucket is full, this peer can be replaced to make space for a new peer.
        private final boolean replaceable;
        private long latency;

        public PeerInfo(@NonNull PeerId peerId, long latency, boolean replaceable) {
            this.peerId = peerId;
            this.latency = latency;
            this.id = Util.ConvertPeerID(peerId);
            this.replaceable = replaceable;
        }

        public long getLatency() {
            return latency;
        }

        public void setLatency(long latency) {
            this.latency = latency;
        }

        public boolean isReplaceable() {
            return replaceable;
        }

        @NonNull
        @Override
        public String toString() {
            return "{" +
                    "latency=" + latency +
                    ", replaceable=" + replaceable +
                    '}';
        }

        @NonNull
        public PeerId getPeerId() {
            return peerId;
        }

        @NonNull
        public ID getID() {
            return id;
        }

    }
}
