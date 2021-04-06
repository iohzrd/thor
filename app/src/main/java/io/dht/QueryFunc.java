package io.dht;

import androidx.annotation.NonNull;

import java.util.List;

import io.Closeable;
import io.libp2p.core.PeerId;

public interface QueryFunc {
    @NonNull
    List<AddrInfo> func(@NonNull Closeable ctx, @NonNull PeerId peerId);
}