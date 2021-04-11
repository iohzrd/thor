package io.dht;

import androidx.annotation.NonNull;

import io.libp2p.core.PeerId;

public class QueryPeerState implements Comparable<QueryPeerState> {
    public final PeerId id;
    private final long distance;// TODO
    private final PeerId referredBy;
    PeerState state;

    public QueryPeerState(@NonNull PeerId id, long distance, @NonNull PeerId referredBy) {
        this.id = id;
        this.distance = distance;
        this.state = PeerState.PeerHeard;
        this.referredBy = referredBy;

    }

    @Override
    public int compareTo(QueryPeerState o) {
        return Long.compare(distance, o.distance);
    }
}
