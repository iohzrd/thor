package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.util.Set;

import io.ipfs.cid.Cid;
import io.core.Closeable;
import io.core.ClosedException;
import io.core.ConnectionIssue;
import io.core.ProtocolIssue;
import io.core.TimeoutIssue;
import io.ipfs.dht.Routing;
import io.ipfs.host.PeerId;


public interface BitSwapNetwork {

    boolean connectTo(@NonNull Closeable closeable, @NonNull PeerId peerId) throws ClosedException, ConnectionIssue;

    void writeMessage(@NonNull Closeable closeable, @NonNull PeerId peer,
                      @NonNull BitSwapMessage message, boolean urgentPriority)
            throws ClosedException, ProtocolIssue, TimeoutIssue, ConnectionIssue;

    void findProviders(@NonNull Closeable closeable, @NonNull Routing.Providers providers,
                       @NonNull Cid cid) throws ClosedException;

    @NonNull
    Set<PeerId> getPeers();

    PeerId Self();
}
