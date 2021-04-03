package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import io.libp2p.core.PeerId;


public interface Receiver {
    void ReceiveMessage(@NonNull PeerId peer, @NonNull String protocol, @NonNull BitSwapMessage incoming);

    void ReceiveError(@NonNull PeerId peer, @NonNull String protocol, @NonNull String error);

    boolean GatePeer(PeerId peerID);
}
