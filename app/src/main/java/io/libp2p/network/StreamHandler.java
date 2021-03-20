package io.libp2p.network;

import androidx.annotation.NonNull;

import io.libp2p.peer.PeerID;

public interface StreamHandler {
    boolean gate(@NonNull PeerID peerID);

    void error(@NonNull Stream stream);

    void message(@NonNull Stream stream);
}
