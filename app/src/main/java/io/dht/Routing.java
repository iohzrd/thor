package io.dht;

import androidx.annotation.NonNull;

import io.Closeable;
import io.libp2p.routing.ContentRouting;

public interface Routing extends ContentRouting, PeerRouting {
    void PutValue(@NonNull Closeable closable, String key, byte[] data);
}
