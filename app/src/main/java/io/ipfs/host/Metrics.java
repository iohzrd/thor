package io.ipfs.host;

import androidx.annotation.NonNull;

import io.libp2p.core.PeerId;

public interface Metrics {
    long getLatency(@NonNull PeerId peerId);

    void addLatency(@NonNull PeerId peerId, long latency);

    boolean isProtected(@NonNull PeerId peerId);

    void active(@NonNull PeerId peerId);

    void done(@NonNull PeerId peerId);
}
