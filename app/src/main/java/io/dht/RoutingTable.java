package io.dht;

import androidx.annotation.NonNull;

import java.util.List;

import io.libp2p.core.PeerId;

public class RoutingTable {
    // ID of the local peer
    private ID local; // TODO make final

    // NearestPeers returns a list of the 'count' closest peers to the given ID
    public List<PeerId> NearestPeers(@NonNull ID id, int count) {

        // This is the number of bits _we_ share with the key. All peers in this
        // bucket share cpl bits with us and will therefore share at least cpl+1
        // bits with the given key. +1 because both the target and all peers in
        // this bucket differ from us in the cpl bit.
        /* TODO
        cpl = CommonPrefixLen(id, this.local);

        // It's assumed that this also protects the buckets.
        rt.tabLock.RLock()

        // Get bucket index or last bucket
        if cpl >= len(rt.buckets) {
        cpl = len(rt.buckets) - 1
    }

        pds:=peerDistanceSorter {
        peers:
        make([]peerDistance, 0, count + rt.bucketsize),
        target:
        id,
    }

        // Add peers from the target bucket (cpl+1 shared bits).
        pds.appendPeersFromList(rt.buckets[cpl].list)

        // If we're short, add peers from all buckets to the right. All buckets
        // to the right share exactly cpl bits (as opposed to the cpl+1 bits
        // shared by the peers in the cpl bucket).
        //
        // This is, unfortunately, less efficient than we'd like. We will switch
        // to a trie implementation eventually which will allow us to find the
        // closest N peers to any target key.

        if pds.Len() < count {
        for i:=cpl + 1;
        i<len (rt.buckets);
        i++ {
            pds.appendPeersFromList(rt.buckets[i].list)
        }
    }

        // If we're still short, add in buckets that share _fewer_ bits. We can
        // do this bucket by bucket because each bucket will share 1 fewer bit
        // than the last.
        //
        // * bucket cpl-1: cpl-1 shared bits.
        // * bucket cpl-2: cpl-2 shared bits.
        // ...
        for i:=cpl - 1;
        i >= 0 && pds.Len() < count;
        i-- {
        pds.appendPeersFromList(rt.buckets[i].list)
    }
        rt.tabLock.RUnlock()

        // Sort by distance to local peer
        pds.sort()

        if count<pds.Len () {
        pds.peers = pds.peers[:count]
    }

        out:=make([]peer.ID, 0, pds.Len())
        for _, p :=range pds.peers {
        out = append(out, p.p)
    }

        return out */
        return null;
    }

}