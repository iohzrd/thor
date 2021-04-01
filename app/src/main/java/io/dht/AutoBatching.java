package io.dht;

import androidx.annotation.NonNull;

public class AutoBatching {
    private final Batching child;

    // TODO: discuss making ds.Batch implement the full ds.Datastore interface
    // buffer           map[ds.Key]op
    private final int maxBufferEntries;
    public AutoBatching(@NonNull Batching dstore, int size){
        this.child = dstore;
        maxBufferEntries = size;
    }
}
