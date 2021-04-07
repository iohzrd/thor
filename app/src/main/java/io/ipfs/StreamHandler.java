package io.ipfs;

import androidx.annotation.NonNull;

import io.libp2p.core.PeerId;


// TODO remove
public interface StreamHandler {
    boolean gate(@NonNull PeerId peerID);

    void error(@NonNull PeerId peerID, @NonNull String protocol, @NonNull String error);

    void message(@NonNull PeerId peerID, @NonNull String protocol, @NonNull byte[] data);
}
