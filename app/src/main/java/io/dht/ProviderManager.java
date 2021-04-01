package io.dht;

import androidx.annotation.NonNull;

import io.libp2p.peer.PeerID;

public class ProviderManager {
    public static final int batchBufferSize = 256;
    private final AutoBatching dstore;
    public ProviderManager(@NonNull Batching store){
        this.dstore = new AutoBatching(store, batchBufferSize);
    }
}
