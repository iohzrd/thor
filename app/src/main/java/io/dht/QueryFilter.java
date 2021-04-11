package io.dht;

import androidx.annotation.NonNull;

import io.libp2p.AddrInfo;

public interface QueryFilter {
    boolean queryPeerFilter(@NonNull KadDHT dht, @NonNull AddrInfo addrInfo);
}

