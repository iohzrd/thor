package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import io.libp2p.peer.PeerID;
import io.libp2p.protocol.Protocol;

public interface Receiver {
    void ReceiveMessage(@NonNull PeerID peer, @NonNull Protocol protocol, @NonNull BitSwapMessage incoming);

    void ReceiveError(@NonNull PeerID peer, @NonNull Protocol protocol, @NonNull String error);

    boolean GatePeer(PeerID peerID);
}
