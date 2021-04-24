package io.ipfs.dht;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

import io.libp2p.core.PeerId;

public class QueryPeerState implements Comparable<QueryPeerState> {

    @NonNull
    public final PeerId id;
    @NonNull
    public final BigInteger distance;
    @NonNull
    private PeerState state;

    public QueryPeerState(@NonNull PeerId id, @NonNull BigInteger distance) {
        this.id = id;
        this.distance = distance;
        this.state = PeerState.PeerHeard;
    }

    @NonNull
    public PeerState getState() {
        return state;
    }

    public void setState(@NonNull PeerState state) {
        this.state = state;
    }

    @NotNull
    @Override
    public String toString() {
        return "QueryPeerState{" +
                "id=" + id +
                ", distance=" + distance +
                ", state=" + state +
                '}';
    }

    @Override
    public int compareTo(QueryPeerState o) {
        return distance.compareTo(o.distance);
    }

}
