package io.ipfs.host;

import androidx.annotation.NonNull;

public interface Metrics {
    long getLatency(@NonNull PeerId peerId);

    void addLatency(@NonNull PeerId peerId, long latency);

    boolean isProtected(@NonNull PeerId peerId);

    void active(@NonNull PeerId peerId);

    void done(@NonNull PeerId peerId);
}
