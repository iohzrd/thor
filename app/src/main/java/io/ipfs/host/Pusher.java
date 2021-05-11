package io.ipfs.host;

import androidx.annotation.NonNull;

public interface Pusher {
    void push(@NonNull PeerId peerId, @NonNull String content);
}
