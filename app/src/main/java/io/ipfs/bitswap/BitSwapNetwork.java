package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.util.Set;

import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.core.ConnectionIssue;
import io.ipfs.dht.Routing;
import io.ipfs.host.PeerId;


public interface BitSwapNetwork {

    void connectTo(@NonNull Closeable closeable, @NonNull PeerId peerId, int timeout)
            throws ClosedException, ConnectionIssue;

    void findProviders(@NonNull Closeable closeable, @NonNull Routing.Providers providers,
                       @NonNull Cid cid) throws ClosedException;

    @NonNull
    Set<PeerId> getPeers();

    PeerId Self();
}
