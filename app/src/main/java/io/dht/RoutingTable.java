package io.dht;

import androidx.annotation.NonNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.LogUtils;
import io.libp2p.Metrics;
import io.libp2p.core.PeerId;

public class RoutingTable {

    private static final String TAG = RoutingTable.class.getSimpleName();
    private final ID local;  // ID of the local peer
    private final ConcurrentHashMap<Integer, Bucket> buckets = new ConcurrentHashMap<>();

    public final long maxLatency = Duration.ofMinutes(1).toMillis();
    private final int bucketSize;
    private final Metrics metrics;


    public RoutingTable(@NonNull Metrics metrics, int bucketSize, @NonNull ID local) {
        this.metrics = metrics;
        this.bucketSize = bucketSize;
        this.local = local;
    }


    // NearestPeers returns a list of the 'count' closest peers to the given ID
    public List<PeerId> NearestPeers(@NonNull ID id, int count) {

        // This is the number of bits _we_ share with the key. All peers in this
        // bucket share cpl bits with us and will therefore share at least cpl+1
        // bits with the given key. +1 because both the target and all peers in
        // this bucket differ from us in the cpl bit.

        int cpl = bucketId(id);

        PeerDistanceSorter pds = new PeerDistanceSorter(id);

        // Add peers from the target bucket (cpl+1 shared bits).
        pds.appendPeersFromList(getBucket(cpl));

        // If we're short, add peers from all buckets to the right. All buckets
        // to the right share exactly cpl bits (as opposed to the cpl+1 bits
        // shared by the peers in the cpl bucket).
        //
        // This is, unfortunately, less efficient than we'd like. We will switch
        // to a trie implementation eventually which will allow us to find the
        // closest N peers to any target key.

        if (pds.size() < count) {
            for (int i = cpl + 1; i < buckets.size(); i++) {
                pds.appendPeersFromList(getBucket(i));
            }
        }

        // If we're still short, add in buckets that share _fewer_ bits. We can
        // do this bucket by bucket because each bucket will share 1 fewer bit
        // than the last.
        //
        // * bucket cpl-1: cpl-1 shared bits.
        // * bucket cpl-2: cpl-2 shared bits.
        // ...
        for (int i = cpl - 1; i >= 0 && pds.size() < count; i--) {
            pds.appendPeersFromList(getBucket(i));
        }

        // Sort by distance to local peer
        Collections.sort(pds);


        List<PeerId> peers = new ArrayList<>();
        for (PeerDistanceSorter.PeerDistance entry : pds) {
            peers.add(entry.getPeerId());
        }

        return peers;
    }

    public int size() {
        int tot = 0;
        for (Bucket bucket : buckets.values()) {
            tot += bucket.size();
        }
        return tot;
    }


    private int bucketIdForPeer(@NonNull PeerId p) {
        ID peerID = Util.ConvertPeerID(p);
        int bucketID = bucketId(peerID);
        LogUtils.info(TAG, "bucketID " + bucketID + " for " + p.toBase58());
        return bucketID;
    }

    private int bucketId(@NonNull ID id) {
        return Util.CommonPrefixLen(id, local);
    }

    private synchronized Bucket getBucket(int cpl) {
        Bucket bucket = buckets.get(cpl);
        if (bucket != null) {
            return bucket;
        }
        bucket = new Bucket();
        buckets.put(cpl, bucket);
        return bucket;
    }

    public void addPeer(@NonNull PeerId p, boolean isReplaceable) {

        synchronized (p.toBase58().intern()) {

            LogUtils.error(TAG, buckets.toString());
            long latency = metrics.getLatency(p);
            int bucketID = bucketIdForPeer(p);
            Bucket bucket = getBucket(bucketID);


            // peer already exists in the Routing Table.
            Bucket.PeerInfo peer = bucket.getPeer(p);
            if (peer != null) {
                peer.setLatency(latency);
                return;
            }

            // peer's latency threshold is NOT acceptable
            if (latency > maxLatency) {
                // Connection doesnt meet requirements, skip!
                return;
            }


            // We have enough space in the bucket (whether spawned or grouped).
            if (bucket.size() < bucketSize) {
                bucket.addPeer(p, latency, isReplaceable);
                return;
            }


            // the bucket to which the peer belongs is full. Let's try to find a peer
            // in that bucket which is replaceable.
            // we don't really need a stable sort here as it dosen't matter which peer we evict
            // as long as it's a replaceable peer.
            Bucket.PeerInfo replaceablePeer = bucket.weakest(((p1, p2) -> {
                boolean result;
                if (p1.isReplaceable()) {
                    if (p2.isReplaceable()) {
                        result = p1.getLatency() < p2.getLatency();
                    } else {
                        result = true;
                    }
                } else {
                    if (p2.isReplaceable()) {
                        result = false;
                    } else {
                        result = p1.getLatency() < p2.getLatency();
                    }
                }
                return result;
            }));

            if (replaceablePeer != null && replaceablePeer.isReplaceable()) {
                // let's evict it and add the new peer
                if (removePeer(replaceablePeer.getPeerId())) {
                    bucket.addPeer(p, latency, isReplaceable);
                    return;
                }
            }

            LogUtils.info(TAG, "Buckets Size :" + buckets.size());
            LogUtils.info(TAG, "Total Size : " + size());
        }

    }

    private boolean removePeer(@NonNull PeerId p) {
        int bucketID = bucketIdForPeer(p);
        Bucket bucket = getBucket(bucketID);
        Objects.requireNonNull(bucket);
        return bucket.removePeer(p);
    }


    public void RemovePeer(@NonNull PeerId p) {
        removePeer(p);
    }
}