package io.libp2p.peer;

import java.util.ArrayList;
import java.util.List;

import io.libp2p.core.multiformats.Multiaddr;

public class AddrInfo {
    private PeerID    ID;
    private List<Multiaddr> multiaddrList = new ArrayList<>();
}
