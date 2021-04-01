package io.dht;

import androidx.annotation.NonNull;

import io.Closeable;

import io.libp2p.peer.AddrInfo;
import io.libp2p.peer.PeerID;

public interface PeerRouting {
    // FindPeer searches for a peer with given ID, returns a peer.AddrInfo
    // with relevant addresses.
    AddrInfo FindPeer(@NonNull Closeable closeable, @NonNull PeerID peerID);
}
