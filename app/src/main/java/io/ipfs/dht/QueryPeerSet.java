package io.ipfs.dht;

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

    private final ID key;
    private final ConcurrentHashMap<PeerId, QueryPeerState> all = new ConcurrentHashMap<>();


    private QueryPeerSet(@NonNull ID key) {
        this.key = key;
    }

    public static QueryPeerSet create(@NonNull byte[] key) {
        return new QueryPeerSet(Util.ConvertKey(key));
    }

    private BigInteger distanceToKey(@NonNull PeerId p) {
        return Util.Distance(Util.ConvertKey(p.getBytes()), key);
    }

    // TryAdd adds the peer p to the peer set.
    // If the peer is already present, no action is taken.
    // Otherwise, the peer is added with state set to PeerHeard.
    // TryAdd returns true iff the peer was not already present.
    public boolean TryAdd(@NonNull PeerId p) {
        QueryPeerState peerset = all.get(p);
        if (peerset != null) {
            return false;
        } else {
            peerset = new QueryPeerState(p, distanceToKey(p));
            all.put(p, peerset);
            return true;
        }
    }

    public PeerState GetState(@NonNull PeerId p) {
        return Objects.requireNonNull(all.get(p)).getState();
    }

    public void SetState(@NonNull PeerId p, @NonNull PeerState peerState) {
        Objects.requireNonNull(all.get(p)).setState(peerState);
    }


    // GetClosestNInStates returns the closest to the key peers, which are in one of the given states.
    // It returns n peers or less, if fewer peers meet the condition.
    // The returned peers are sorted in ascending order by their distance to the key.
    public List<QueryPeerState> GetClosestNInStates(int maxLength, List<PeerState> states) {
        if (LogUtils.isDebug()) {
            if (maxLength < 0) {
                throw new RuntimeException("internal state error");
            }
        }
        List<QueryPeerState> list = new ArrayList<>(all.values());
        Collections.sort(list);

        List<QueryPeerState> peers = new ArrayList<>();
        int count = 0;
        for (QueryPeerState state : list) {
            if (states.contains(state.getState())) {
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

    @NonNull
    List<QueryPeerState> GetClosestInStates(int maxLength, @NonNull List<PeerState> states) {
        return GetClosestNInStates(maxLength, states);
    }

    public int NumWaiting() {
        return GetClosestInStates(all.size(), Collections.singletonList(PeerState.PeerWaiting)).size();
    }

    public int NumHeard() {
        return GetClosestInStates(all.size(), Collections.singletonList(PeerState.PeerHeard)).size();
    }
}
