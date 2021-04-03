package io.dht;

import androidx.annotation.NonNull;


public class ProviderManager {
    public static final int batchBufferSize = 256;
    private final AutoBatching dstore;
    public ProviderManager(@NonNull Batching store){
        this.dstore = new AutoBatching(store, batchBufferSize);
    }
}
