package threads.lite.dht;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import threads.lite.LogUtils;
import threads.lite.cid.PeerId;

public class RoutingTable {

    private static final String TAG = RoutingTable.class.getSimpleName();
    private final ID local;  // ID of the local peer
    private final ConcurrentHashMap<Integer, Bucket> buckets = new ConcurrentHashMap<>();
    private final int bucketSize;


    public RoutingTable(int bucketSize, @NonNull ID local) {
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
        ID peerID = ID.convertPeerID(p);
        int bucketID = bucketId(peerID);
        LogUtils.info(TAG, "bucketID " + bucketID + " for " + p.toBase58());
        return bucketID;
    }

    private int bucketId(@NonNull ID id) {
        return Util.CommonPrefixLen(id, local);
    }

    private Bucket getBucket(int cpl) {
        synchronized (TAG.intern()) {
            Bucket bucket = buckets.get(cpl);
            if (bucket != null) {
                return bucket;
            }
            bucket = new Bucket();
            buckets.put(cpl, bucket);
            return bucket;
        }
    }

    public void addPeer(@NonNull PeerId peerId, boolean isReplaceable) {

        try {
            int bucketID = bucketIdForPeer(peerId);
            Bucket bucket = getBucket(bucketID);

            // peer already exists in the Routing Table.
            if (bucket.containsPeer(peerId)) {
                return;
            }


            // We have enough space in the bucket
            if (bucket.size() < bucketSize) {
                bucket.addPeer(peerId, isReplaceable);
                return;
            }


            // the bucket to which the peer belongs is full. Let's try to find a peer
            // in that bucket which is replaceable.
            // we don't really need a stable sort here as it doesn't matter which peer we evict
            // as long as it's a replaceable peer.
            PeerId replaceablePeer = bucket.weakest();

            if (replaceablePeer != null) {
                // let's evict it and add the new peer
                if (removePeer(replaceablePeer)) {
                    bucket.addPeer(peerId, isReplaceable);
                }
            }
        } finally {
            LogUtils.verbose(TAG, buckets.toString());
        }
    }

    boolean removePeer(@NonNull PeerId p) {
        int bucketID = bucketIdForPeer(p);
        Bucket bucket = getBucket(bucketID);
        Objects.requireNonNull(bucket);
        return bucket.removePeer(p);
    }


}