package io.dht;

import androidx.annotation.NonNull;

import io.libp2p.AddrInfo;

public interface Providers {
    void Peer(@NonNull AddrInfo addrInfo);
}
