package io.dht;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.LogUtils;
import io.libp2p.core.PeerId;

public class Bucket {
    private static final String TAG = Bucket.class.getSimpleName();
    private final ConcurrentHashMap<PeerId, PeerInfo> peers = new ConcurrentHashMap<>();

    @Nullable
    public PeerInfo getPeer(@NonNull PeerId p) {
        return peers.get(p);
    }

    @Nullable
    public PeerInfo min(@NonNull MinFunc func) {
        if (size() == 0) {
            return null;
        }
        PeerInfo minVal = null;
        for (Map.Entry<PeerId, PeerInfo> entry : peers.entrySet()) {
            if (minVal == null) {
                minVal = entry.getValue();
            } else {
                if (func.less(entry.getValue(), minVal)) {
                    minVal = entry.getValue();
                }
            }
        }
        return minVal;
    }

    public synchronized Bucket split(int cpl, @NonNull ID target) {

        Bucket newbie = new Bucket();

        for (PeerInfo e :  elements()) {
            ID pDhtId = e.getID();
            int peerCPL = Util.CommonPrefixLen(pDhtId, target);
            if (peerCPL > cpl) {
                newbie.pushFront(e);
                peers.remove(e.getPeerId());
            }
        }
        return newbie;
    }

    @NotNull
    @Override
    public String toString() {
        return "Bucket{" +
                "peers=" + peers.size() +
                '}';
    }

    public void addPeer(@NonNull PeerId p, boolean isReplaceable, long lastUsefulAt, long now) {
        Bucket.PeerInfo peerInfo = new Bucket.PeerInfo(p, isReplaceable);
        peerInfo.LastUsefulAt = lastUsefulAt;
        peerInfo.LastSuccessfulOutboundQueryAt = now;
        peerInfo.AddedAt = now;
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
        // LastUsefulAt is the time instant at which the peer was last "useful" to us.
        // Please see the DHT docs for the definition of usefulness.
        long LastUsefulAt;

        // LastSuccessfulOutboundQueryAt is the time instant at which we last got a
        // successful query response from the peer.
        long LastSuccessfulOutboundQueryAt;

        // AddedAt is the time this peer was added to the routing table.
        long AddedAt;

        // if a bucket is full, this peer can be replaced to make space for a new peer.
        private final boolean replaceable;

        public PeerInfo(@NonNull PeerId peerId, boolean replaceable) {
            this.peerId = peerId;
            this.id = Util.ConvertPeerID(peerId);
            this.replaceable = replaceable;
        }

        public boolean isReplaceable() {
            return replaceable;
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
