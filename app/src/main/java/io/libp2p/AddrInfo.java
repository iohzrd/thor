package io.libp2p;

import androidx.annotation.NonNull;

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

    @NonNull
    public PeerId getPeerId() {
        return peerId;
    }

    public Multiaddr[] getAddresses() {
        return addresses.toArray(new Multiaddr[addresses.size()]);
    }

    public void addAddresses(@NonNull Collection<Multiaddr> addresses) {
        for (Multiaddr address : addresses) {
            if (!this.addresses.contains(address)) {
                this.addresses.add(address);
            }
        }
    }
}
