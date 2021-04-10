package io.dht;

import androidx.annotation.NonNull;

import io.Closeable;
import io.libp2p.AddrInfo;

public interface Providers extends Closeable {
    void Peer(@NonNull AddrInfo addrInfo);
}
