package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.util.Set;

import io.core.Closeable;
import io.core.ConnectionFailure;
import io.libp2p.AddrInfo;
import io.dht.ContentRouting;
import io.core.ClosedException;
import io.core.ProtocolNotSupported;
import io.libp2p.core.PeerId;


public interface BitSwapNetwork extends ContentRouting {

    boolean ConnectTo(@NonNull Closeable closeable, @NonNull AddrInfo addrInfo,
                      boolean protect) throws ClosedException;

    void WriteMessage(@NonNull Closeable closeable, @NonNull PeerId peer,
                      @NonNull BitSwapMessage message, int timeout)
            throws ClosedException, ProtocolNotSupported, ConnectionFailure;

    @NonNull
    Set<PeerId> getPeers();
}
