package io.ipfs.push;

import androidx.annotation.NonNull;

import io.ipfs.host.PeerId;

public interface Pusher {
    void push(@NonNull PeerId peerId, @NonNull String content);
}
