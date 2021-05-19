package io.ipfs.host;

import androidx.annotation.NonNull;

public interface Metrics {
    long getLatency(@NonNull PeerId peerId);

    boolean isProtected(@NonNull PeerId peerId);
}
