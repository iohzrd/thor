package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import io.ipfs.host.PeerId;


public interface BitSwapReceiver {
    boolean gatePeer(@NonNull PeerId peerID);

    void receiveMessage(@NonNull PeerId peer, @NonNull BitSwapMessage incoming);
}
