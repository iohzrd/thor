package io.dht;

import java.util.ArrayList;
import java.util.List;

import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;


public class AddrInfo {
    private PeerId ID;
    private List<Multiaddr> multiaddrList = new ArrayList<>();
}
