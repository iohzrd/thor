package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import io.ipfs.host.PeerId;


public interface BitSwapReceiver {
    void ReceiveMessage(@NonNull PeerId peer, @NonNull String protocol, @NonNull BitSwapMessage incoming);

    void ReceiveError(@NonNull PeerId peer, @NonNull String protocol, @NonNull String error);

    boolean GatePeer(PeerId peerID);
}
