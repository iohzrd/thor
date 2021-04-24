package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.util.Set;

import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.core.ConnectionIssue;
import io.ipfs.core.ProtocolIssue;
import io.ipfs.core.TimeoutIssue;
import io.ipfs.dht.Routing;
import io.libp2p.core.PeerId;


public interface BitSwapNetwork {

    boolean connectTo(@NonNull Closeable closeable, @NonNull PeerId peerId) throws ClosedException, ConnectionIssue;

    void writeMessage(@NonNull Closeable closeable, @NonNull PeerId peer,
                      @NonNull BitSwapMessage message)
            throws ClosedException, ProtocolIssue, TimeoutIssue, ConnectionIssue;

    void findProviders(@NonNull Closeable closeable, @NonNull Routing.Providers providers,
                       @NonNull Cid cid) throws ClosedException;

    @NonNull
    Set<PeerId> getPeers();

    PeerId Self();
}
