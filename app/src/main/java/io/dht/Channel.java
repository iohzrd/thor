package io.dht;

import androidx.annotation.NonNull;

import io.core.ClosedException;
import io.libp2p.AddrInfo;

public interface Channel {
    void peer(@NonNull AddrInfo addrInfo) throws ClosedException;
}
