package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import io.ipfs.host.PeerId;


public interface BitSwapReceiver {
    boolean GatePeer(@NonNull PeerId peerID);

    boolean ReceiveMessage(@NonNull PeerId peer, @NonNull BitSwapMessage incoming);
}
