package io.dht;

import androidx.annotation.NonNull;

import java.math.BigInteger;

import io.libp2p.core.PeerId;

public class QueryPeerState implements Comparable<QueryPeerState> {
    public final PeerId id;
    private final BigInteger distance;
    private final PeerId referredBy;
    PeerState state;

    public QueryPeerState(@NonNull PeerId id, BigInteger distance, @NonNull PeerId referredBy) {
        this.id = id;
        this.distance = distance;
        this.state = PeerState.PeerHeard;
        this.referredBy = referredBy;

    }

    @Override
    public int compareTo(QueryPeerState o) {
        return distance.compareTo(o.distance); // TODO check
    }
}
