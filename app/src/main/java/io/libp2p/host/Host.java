package io.libp2p.host;

import androidx.annotation.NonNull;

import java.util.List;

import io.Closeable;
import io.ipfs.ClosedException;
import io.ipfs.ProtocolNotSupported;
import io.libp2p.network.StreamHandler;
import io.libp2p.peer.PeerID;
import io.libp2p.protocol.Protocol;


public interface Host {
    boolean Connect(@NonNull Closeable ctx, @NonNull PeerID peer, boolean protect) throws ClosedException;

    long WriteMessage(@NonNull Closeable closeable,
                      @NonNull PeerID peer,
                      @NonNull List<Protocol> protocols,
                      @NonNull byte[] bytes) throws ClosedException, ProtocolNotSupported;


    // SetStreamHandler sets the protocol handler on the Host's Mux.
    // This is equivalent to:
    //   host.Mux().SetHandler(proto, handler)
    // (Threadsafe)
    void SetStreamHandler(@NonNull Protocol proto, @NonNull StreamHandler handler);

    PeerID Self();

    List<PeerID> getPeers();
}
