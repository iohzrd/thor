package io.dht;

import androidx.annotation.NonNull;

import io.core.Closeable;

public interface Routing extends ContentRouting, PeerRouting, ValueStore {
    void PutValue(@NonNull Closeable closable, String key, byte[] data);
    void init();
}
