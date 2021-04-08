package io.dht;

import androidx.annotation.NonNull;

import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.libp2p.core.PeerId;

public class RoutingTable {

    private final ID local;  // ID of the local peer
    private final List<Bucket> buckets = new ArrayList<>();
    private final int bucketsize;

    public RoutingTable(int bucketsize, @NonNull ID local) {
        this.bucketsize = bucketsize;
        this.local = local;
        buckets.add(new Bucket());
    }

    public static ID xor(ID a, ID b) {
        byte[] res = ByteUtils.xor(a.data, b.data);
        return new ID(res);
    }

    // ZeroPrefixLen returns the number of consecutive zeroes in a byte slice.
    private int ZeroPrefixLen(byte[] id) {

        for (int i = 0; i < id.length; i++) {
            byte b = id[i];
            if (b != 0) {
                // TODO check if really is correct
                return i * 8 + /*bits.LeadingZeros8(uint8(b))*/  Integer.numberOfLeadingZeros(b);
            }
        }
        return id.length * 8;

    }

    private int CommonPrefixLen(ID a, ID b) {
        byte[] res = ByteUtils.xor(a.data, b.data);
        return ZeroPrefixLen(res);
    }

    // NearestPeers returns a list of the 'count' closest peers to the given ID
    public List<PeerId> NearestPeers(@NonNull ID id, int count) {

        // This is the number of bits _we_ share with the key. All peers in this
        // bucket share cpl bits with us and will therefore share at least cpl+1
        // bits with the given key. +1 because both the target and all peers in
        // this bucket differ from us in the cpl bit.

        int cpl = CommonPrefixLen(id, this.local);

        // It's assumed that this also protects the buckets.
        // rt.tabLock.RLock() TODO ???

        // Get bucket index or last bucket
        if (cpl >= buckets.size()) {
            cpl = buckets.size() - 1;
        }

        PeerDistanceSorter pds = new PeerDistanceSorter(id);

        // Add peers from the target bucket (cpl+1 shared bits).
        pds.appendPeersFromList(buckets.get(cpl));

        // If we're short, add peers from all buckets to the right. All buckets
        // to the right share exactly cpl bits (as opposed to the cpl+1 bits
        // shared by the peers in the cpl bucket).
        //
        // This is, unfortunately, less efficient than we'd like. We will switch
        // to a trie implementation eventually which will allow us to find the
        // closest N peers to any target key.

        if (pds.size() < count) {
            for (int i = cpl + 1; i < buckets.size(); i++) {
                pds.appendPeersFromList(buckets.get(i));
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
            pds.appendPeersFromList(buckets.get(i));
        }
        //rt.tabLock.RUnlock()

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
        for (Bucket bucket : buckets) {
            tot += bucket.size();
        }
        return tot;
    }

    public boolean TryAddPeer(PeerId p, Boolean queryPeer, boolean isReplaceable) {
        return addPeer(p, queryPeer, isReplaceable);
    }


    // the caller is responsible for the locking
    private int bucketIdForPeer(PeerId p) {
        ID peerID = Util.ConvertPeerID(p);
        int bucketID = CommonPrefixLen(peerID, local);
        if (bucketID >= buckets.size()) {
            bucketID = buckets.size() - 1;
        }
        return bucketID;
    }

    // locking is the responsibility of the caller
    private boolean addPeer(PeerId p, Boolean queryPeer, boolean isReplaceable) {

        int bucketID = bucketIdForPeer(p);
        Bucket bucket = buckets.get(bucketID);

        long now = System.currentTimeMillis();
        long lastUsefulAt = 0;
        if (queryPeer) {
            lastUsefulAt = now;
        }

        // peer already exists in the Routing Table.
        Bucket.PeerInfo peer = bucket.getPeer(p);
        if (peer != null) {
            // if we're querying the peer first time after adding it, let's give it a
            // usefulness bump. This will ONLY happen once.
            if (peer.LastUsefulAt == 0 && queryPeer) {
                peer.LastUsefulAt = lastUsefulAt;
            }
            return false;
        }
/* TODO
            // peer's latency threshold is NOT acceptable
            if rt.metrics.LatencyEWMA(p) > rt.maxLatency {
                // Connection doesnt meet requirements, skip!
                return false, ErrPeerRejectedHighLatency
            }

            // add it to the diversity filter for now.
            // if we aren't able to find a place for the peer in the table,
            // we will simply remove it from the Filter later.
            if rt.df != nil {
                if !rt.df.TryAdd(p) {
                    return false, errors.New("peer rejected by the diversity filter")
                }
            }*/

        // We have enough space in the bucket (whether spawned or grouped).
        if (bucket.size() < bucketsize) {
            Bucket.PeerInfo peerInfo = new Bucket.PeerInfo(p, Util.ConvertPeerID(p));
            peerInfo.LastUsefulAt = lastUsefulAt;
            peerInfo.LastSuccessfulOutboundQueryAt = now;
            peerInfo.AddedAt = now;
            peerInfo.replaceable = isReplaceable;
            bucket.addFirst(peerInfo);
            // TODO PeerAdded(p); (notification func)
            return true;
        }
        /*
            if bucketID == len(rt.buckets)-1 {
                // if the bucket is too large and this is the last bucket (i.e. wildcard), unfold it.
                rt.nextBucket()
                // the structure of the table has changed, so let's recheck if the peer now has a dedicated bucket.
                bucketID = rt.bucketIdForPeer(p)
                bucket = rt.buckets[bucketID]

                // push the peer only if the bucket isn't overflowing after slitting
                if bucket.len() < rt.bucketsize {
                    bucket.pushFront(&PeerInfo{
                        Id:                            p,
                                LastUsefulAt:                  lastUsefulAt,
                                LastSuccessfulOutboundQueryAt: now,
                                AddedAt:                       now,
                                dhtId:                         ConvertPeerID(p),
                                replaceable:                   isReplaceable,
                    })
                    rt.PeerAdded(p)
                    return true, nil
                }
            }

            // the bucket to which the peer belongs is full. Let's try to find a peer
            // in that bucket which is replaceable.
            // we don't really need a stable sort here as it dosen't matter which peer we evict
            // as long as it's a replaceable peer.
            replaceablePeer := bucket.min(func(p1 *PeerInfo, p2 *PeerInfo) bool {
                return p1.replaceable
            })

            if replaceablePeer != nil && replaceablePeer.replaceable {
                // let's evict it and add the new peer
                if rt.removePeer(replaceablePeer.Id) {
                    bucket.pushFront(&PeerInfo{
                        Id:                            p,
                                LastUsefulAt:                  lastUsefulAt,
                                LastSuccessfulOutboundQueryAt: now,
                                AddedAt:                       now,
                                dhtId:                         ConvertPeerID(p),
                                replaceable:                   isReplaceable,
                    })
                    rt.PeerAdded(p)
                    return true, nil
                }
            }

            // we weren't able to find place for the peer, remove it from the filter state.
            if rt.df != nil {
                rt.df.Remove(p)

            */
        throw new RuntimeException("ErrPeerRejectedNoCapacity");// TODO
    }


    public boolean UpdateLastSuccessfulOutboundQueryAt(@NonNull PeerId p, long t) {


        int bucketID = bucketIdForPeer(p);
        Bucket bucket = buckets.get(bucketID);

        Bucket.PeerInfo peer = bucket.getPeer(p);
        if (peer != null) {
            peer.LastSuccessfulOutboundQueryAt = t;
            return true;
        }

        return false;
    }
}