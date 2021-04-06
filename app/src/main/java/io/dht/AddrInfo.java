package io.dht;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;


public class AddrInfo {
    private final PeerId ID;
    private List<Multiaddr> multiaddrList = new ArrayList<>();

    public AddrInfo(@NonNull PeerId id, @NonNull Multiaddr remoteAddress) {
        this.ID = id;
        this.multiaddrList.add(remoteAddress);
    }
    public AddrInfo(@NonNull PeerId id) {
        this.ID = id;
    }
}
