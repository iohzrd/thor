package io.libp2p.network;

import androidx.annotation.NonNull;

import io.libp2p.peer.PeerID;
import io.libp2p.protocol.Protocol;

public interface StreamHandler {
    boolean gate(@NonNull PeerID peerID);

    void error(@NonNull PeerID peerID, @NonNull Protocol protocol, @NonNull String error);

    void message(@NonNull PeerID peerID, @NonNull Protocol protocol, @NonNull byte[] data);
}
