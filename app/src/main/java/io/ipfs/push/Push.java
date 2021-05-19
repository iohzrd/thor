package io.ipfs.push;

import androidx.annotation.NonNull;

import io.ipfs.host.PeerId;

public interface Push {
    void push(@NonNull PeerId peerId, @NonNull String content);
}
