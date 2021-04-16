package io.dht;

import androidx.annotation.NonNull;

import java.math.BigInteger;

import io.LogUtils;
import io.libp2p.core.PeerId;

public class QueryPeerState implements Comparable<QueryPeerState> {
    private static final String TAG = QueryPeerState.class.getSimpleName();
    public final PeerId id;
    public final BigInteger distance;
    private final PeerId referredBy;
    private PeerState state;

    @NonNull
    public PeerState getState() {
        return state;
    }

    public void setState(@NonNull PeerState state) {
        this.state = state;
    }

    public QueryPeerState(@NonNull PeerId id, BigInteger distance, @NonNull PeerId referredBy) {
        this.id = id;
        this.distance = distance;
        this.state = PeerState.PeerHeard;
        this.referredBy = referredBy;

    }

    @Override
    public String toString() {
        return "QueryPeerState{" +
                "id=" + id +
                ", distance=" + distance +
                ", referredBy=" + referredBy +
                ", state=" + state +
                '}';
    }

    @Override
    public int compareTo(QueryPeerState o) {
        return distance.compareTo(o.distance);
    }

}
