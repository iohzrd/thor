package io.ipfs.host;

import androidx.annotation.NonNull;

import io.libp2p.core.PeerId;

public interface Pusher {
    void push(@NonNull PeerId peerId, @NonNull String content);
}
