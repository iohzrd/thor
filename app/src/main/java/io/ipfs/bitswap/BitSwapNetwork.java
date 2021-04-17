package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.util.Set;

import io.core.Closeable;
import io.core.ClosedException;
import io.core.ConnectionFailure;
import io.core.ConnectionIssue;
import io.core.ProtocolIssue;
import io.dht.Channel;
import io.ipfs.cid.Cid;
import io.libp2p.AddrInfo;
import io.libp2p.core.PeerId;


public interface BitSwapNetwork {

    boolean ConnectTo(@NonNull Closeable closeable, @NonNull AddrInfo addrInfo,
                      boolean protect) throws ClosedException;

    void WriteMessage(@NonNull Closeable closeable, @NonNull PeerId peer,
                      @NonNull BitSwapMessage message)
            throws ClosedException, ProtocolIssue, ConnectionFailure, ConnectionIssue;

    void FindProvidersAsync(@NonNull Closeable closeable, @NonNull Channel channel,
                            @NonNull Cid cid) throws ClosedException;

    @NonNull
    Set<PeerId> getPeers();

    PeerId Self();
}
