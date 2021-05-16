package io.ipfs.host;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.ipfs.multiaddr.Multiaddr;

public class PeerInfo {
    @NonNull
    private final PeerId peerId;
    @NonNull
    private final String agent;
    @NonNull
    private final Multiaddr address;
    @Nullable
    private final Multiaddr observed;

    public PeerInfo(@NonNull PeerId peerId, @NonNull String agent, @NonNull Multiaddr address,
                    @Nullable Multiaddr observed) {
        this.peerId = peerId;
        this.agent = agent;
        this.address = address;
        this.observed = observed;
    }

    // TODO this is probably not useful
    @Nullable
    public Multiaddr getObserved() {
        return observed;
    }

    @NonNull
    public Multiaddr getAddress() {
        return address;
    }

    @NonNull
    @Override
    public String toString() {
        return "PeerInfo{" +
                "peerId=" + peerId +
                ", agent='" + agent + '\'' +
                ", address=" + address +
                ", observed=" + observed +
                '}';
    }

    @NonNull
    public PeerId getPeerId() {
        return peerId;
    }

    @NonNull
    public String getAgent() {
        return agent;
    }

}
