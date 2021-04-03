package io.dht;

import androidx.annotation.NonNull;

import io.Closeable;

public interface Routing extends ContentRouting, PeerRouting, ValueStore {
    void PutValue(@NonNull Closeable closable, String key, byte[] data);
}
