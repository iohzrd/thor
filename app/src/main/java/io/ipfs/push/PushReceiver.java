package io.ipfs.push;

import androidx.annotation.NonNull;

import io.libp2p.core.PeerId;

public interface PushReceiver {
    boolean acceptPusher(@NonNull PeerId remotePeerId);

    void pushMessage(@NonNull PeerId remotePeerId, @NonNull byte[] toByteArray);
}
