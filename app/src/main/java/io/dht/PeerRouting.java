package io.dht;

import androidx.annotation.NonNull;

import io.Closeable;
import io.ipfs.ClosedException;
import io.libp2p.AddrInfo;
import io.libp2p.core.PeerId;


public interface PeerRouting {
    // FindPeer searches for a peer with given ID, returns a peer.AddrInfo
    // with relevant addresses.
    AddrInfo FindPeer(@NonNull Closeable closeable, @NonNull PeerId peerID) throws ClosedException;
}
