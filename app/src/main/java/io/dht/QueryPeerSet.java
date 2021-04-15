package io.dht;

import androidx.annotation.NonNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.LogUtils;
import io.libp2p.core.PeerId;

public class QueryPeerSet {
    private static final String TAG = QueryPeerSet.class.getSimpleName();
    private final QueryKey key;
    private final ConcurrentHashMap<PeerId, QueryPeerState> all = new ConcurrentHashMap<>();
    boolean sorted;

    private QueryPeerSet(@NonNull QueryKey key) {
        this.key = key;
        this.sorted = false;
    }


    // NewQueryPeerset creates a new empty set of peers.
    // key is the target key of the lookup that this peer set is for.
    public static QueryPeerSet create(@NonNull byte[] key) {
        return new QueryPeerSet(QueryKey.createQueryKey(key));
    }

    private BigInteger distanceToKey(@NonNull PeerId p) {
        return QueryKey.createQueryKey(p.getBytes()).Distance(key);
    }

    // TryAdd adds the peer p to the peer set.
    // If the peer is already present, no action is taken.
    // Otherwise, the peer is added with state set to PeerHeard.
    // TryAdd returns true iff the peer was not already present.
    public boolean TryAdd(@NonNull PeerId p, @NonNull PeerId referredBy) {
        QueryPeerState peerset = all.get(p);
        if (peerset != null) {
            return false;
        } else {
            peerset = new QueryPeerState(p, distanceToKey(p), referredBy);
            all.put(p, peerset);
            sorted = false;
            return true;
        }
    }

    public PeerState GetState(@NonNull PeerId p) {
        return Objects.requireNonNull(all.get(p)).state;
    }

    public void SetState(@NonNull PeerId p, @NonNull PeerState peerState) {
        Objects.requireNonNull(all.get(p)).state = peerState;
    }


    // GetClosestNInStates returns the closest to the key peers, which are in one of the given states.
    // It returns n peers or less, if fewer peers meet the condition.
    // The returned peers are sorted in ascending order by their distance to the key.
    public List<QueryPeerState> GetClosestNInStates(int maxLength, List<PeerState> states) {
        List<QueryPeerState> peers = new ArrayList<>();
        int count = 0;
        for (QueryPeerState state : all.values()) {
            if (states.contains(state.state)) {
                peers.add(state);
                count++;
                if (count == maxLength) {
                    break;
                }
            }
        }
        Collections.sort(peers);

        return peers;
    }

    // GetClosestInStates returns the peers, which are in one of the given states.
    // The returned peers are sorted in ascending order by their distance to the key.
    List<QueryPeerState> GetClosestInStates(List<PeerState> states) {
        return GetClosestNInStates(all.size(), states);
    }

    // NumWaiting returns the number of peers in state PeerWaiting.
    public int NumWaiting() {
        return GetClosestInStates(Collections.singletonList(PeerState.PeerWaiting)).size();
    }

    public int NumHeard() {
        return GetClosestInStates(Collections.singletonList(PeerState.PeerHeard)).size();
    }
}
