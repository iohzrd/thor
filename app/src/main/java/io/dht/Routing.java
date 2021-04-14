package io.dht;

import androidx.annotation.NonNull;

import io.core.Closeable;
import io.core.ClosedException;

public interface Routing extends ContentRouting, PeerRouting, ValueStore {
    int PutValue(@NonNull Closeable closable,
                 @NonNull byte[] key,
                 @NonNull byte[] data) throws ClosedException;

    void init();
}
