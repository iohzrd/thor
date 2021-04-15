package io.libp2p;

import androidx.annotation.NonNull;

import com.google.common.collect.Iterables;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;


public class AddrInfo {
    @NonNull
    private final PeerId peerId;
    private final List<Multiaddr> addresses = new ArrayList<>();

    public AddrInfo(@NonNull PeerId id, @NonNull Multiaddr remoteAddress) {
        this.peerId = id;
        this.addresses.add(remoteAddress);
    }

    public AddrInfo(@NonNull PeerId id, @NonNull Collection<Multiaddr> remoteAddresses) {
        this.peerId = id;
        this.addresses.addAll(remoteAddresses);
    }

    @NotNull
    @Override
    public String toString() {
        return "AddrInfo{" +
                "peerId=" + peerId +
                ", addresses=" + addresses +
                '}';
    }

    @NonNull
    public PeerId getPeerId() {
        return peerId;
    }

    public Multiaddr[] getAddresses() {
        return Iterables.toArray(addresses, Multiaddr.class);
    }

    public void addAddresses(@NonNull Multiaddr... addresses) {
        for (Multiaddr address : addresses) {
            if (!this.addresses.contains(address)) {
                this.addresses.add(address);
            }
        }
    }

    public boolean hasAddresses() {
        return !this.addresses.isEmpty();
    }
}
