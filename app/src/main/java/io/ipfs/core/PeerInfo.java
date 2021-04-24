package io.ipfs.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;

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

    @Nullable
    public Multiaddr getObserved() {
        return observed;
    }

    @NotNull
    public Multiaddr getAddress() {
        return address;
    }

    @Override
    public String toString() {
        return "PeerInfo{" +
                "peerId=" + peerId +
                ", agent='" + agent + '\'' +
                ", address=" + address +
                ", observed=" + observed +
                '}';
    }

    @NotNull
    public PeerId getPeerId() {
        return peerId;
    }

    @NotNull
    public String getAgent() {
        return agent;
    }

}
