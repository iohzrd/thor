package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.util.List;

import io.Closeable;
import io.ipfs.ClosedException;
import io.libp2p.peer.PeerID;
import io.libp2p.protocol.Protocol;
import io.libp2p.routing.ContentRouting;

public interface BitSwapNetwork extends ContentRouting {

    boolean ConnectTo(@NonNull Closeable closeable, @NonNull PeerID peer, boolean protect) throws ClosedException;

    void WriteMessage(@NonNull Closeable closeable, @NonNull PeerID peer, @NonNull BitSwapMessage message) throws ClosedException;

    void WriteMessage(@NonNull Closeable closeable, @NonNull PeerID peer, @NonNull Protocol protocol, @NonNull BitSwapMessage message) throws ClosedException;

    void SetDelegate(@NonNull Receiver receiver);

    PeerID Self();

    @NonNull
    List<PeerID> getPeers();
}
