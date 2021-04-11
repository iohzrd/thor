package io.dht;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListSet;

import io.LogUtils;
import io.libp2p.core.PeerId;

public class Bucket extends ConcurrentSkipListSet<Bucket.PeerInfo> {
    private static final String TAG = Bucket.class.getSimpleName();

    @Nullable
    public PeerInfo getPeer(@NonNull PeerId p) {
        for (PeerInfo peerInfo : this) {
            if (Objects.equals(peerInfo.getPeerId(), p)) {
                return peerInfo;
            }
        }
        return null;
    }

    public PeerInfo min(@NonNull MinFunc func) {
        if (size() == 0) {
            return null;
        }

        PeerInfo minVal = first();
        for (PeerInfo e : this) {
            if (func.less(e, minVal)) {
                minVal = e;
            }
        }

        return minVal;
    }

    // splits a buckets peers into two buckets, the methods receiver will have
    // peers with CPL equal to cpl, the returned bucket will have peers with CPL
    // greater than cpl (returned bucket has closer peers)
    public Bucket split(int cpl, @NonNull ID target) {

        LogUtils.info(TAG, "cpl " + cpl);
        Bucket newbie = new Bucket();

        for (PeerInfo e : this) {
            ID pDhtId = e.getID();
            int peerCPL = Util.CommonPrefixLen(pDhtId, target);
            if (peerCPL > cpl) {
                newbie.add(e);
                this.remove(e);
            }
        }
        LogUtils.info(TAG, "Old Bucket Size : " + this.size());
        LogUtils.info(TAG, "New Bucket Size : " + newbie.size());
        return newbie;
    }


    // removes the peer with the given Id from the bucket.
    // returns true if successful, false otherwise.
    public boolean removePeerInfo(@NonNull PeerId p) {

        for (PeerInfo e : this) {
            if (Objects.equals(e.getPeerId(), p)) {
                return remove(e);
            }
        }
        return false;

    }

    public interface MinFunc {
        boolean less(@NonNull PeerInfo p1, @NonNull PeerInfo p2);
    }

    public static class PeerInfo implements Comparable<PeerInfo> {
        @NonNull
        private final PeerId peerId;
        // Id of the peer in the DHT XOR keyspace
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
        public boolean replaceable;

        public PeerInfo(@NonNull PeerId peerId, @NonNull ID id) {
            this.peerId = peerId;
            this.id = id;
        }

        @NonNull
        public PeerId getPeerId() {
            return peerId;
        }

        public ID getID() {
            return id;
        }

        @Override
        public int compareTo(PeerInfo o) {
            return this.peerId.toHex().compareTo(o.getPeerId().toHex());
        }
    }
}
