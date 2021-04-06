package io.dht;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;


public class AddrInfo {
    public final PeerId ID;
    private final List<Multiaddr> addresses = new ArrayList<>();

    public AddrInfo(@NonNull PeerId id, @NonNull Multiaddr remoteAddress) {
        this.ID = id;
        this.addresses.add(remoteAddress);
    }

    public AddrInfo(@NonNull PeerId id, @NonNull List<Multiaddr> remoteAddresses) {
        this.ID = id;
        this.addresses.addAll(remoteAddresses);
    }

    public AddrInfo(@NonNull PeerId id) {
        this.ID = id;
    }

    public List<Multiaddr> getAddresses() {
        return new ArrayList<>(addresses);
    }
}
