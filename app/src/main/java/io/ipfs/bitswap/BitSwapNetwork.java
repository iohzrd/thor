package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.util.Set;

import io.core.Closeable;
import io.core.ClosedException;
import io.core.ConnectionFailure;
import io.core.ConnectionIssue;
import io.core.ProtocolIssue;
import io.dht.Routing;
import io.ipfs.cid.Cid;
import io.libp2p.core.PeerId;


public interface BitSwapNetwork {

    boolean ConnectTo(@NonNull Closeable closeable, @NonNull PeerId peerId,
                      boolean protect) throws ClosedException, ConnectionIssue;

    void WriteMessage(@NonNull Closeable closeable, @NonNull PeerId peer,
                      @NonNull BitSwapMessage message)
            throws ClosedException, ProtocolIssue, ConnectionFailure, ConnectionIssue;

    void FindProviders(@NonNull Closeable closeable, @NonNull Routing.Providers providers,
                       @NonNull Cid cid) throws ClosedException;

    @NonNull
    Set<PeerId> getPeers();

    PeerId Self();
}
