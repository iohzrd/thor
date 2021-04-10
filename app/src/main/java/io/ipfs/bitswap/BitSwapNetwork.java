package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.util.Set;

import io.Closeable;
import io.ipfs.ConnectionNotSupported;
import io.libp2p.AddrInfo;
import io.dht.ContentRouting;
import io.ipfs.ClosedException;
import io.ipfs.ProtocolNotSupported;
import io.libp2p.core.PeerId;


public interface BitSwapNetwork extends ContentRouting {

    boolean ConnectTo(@NonNull Closeable closeable, @NonNull AddrInfo addrInfo,
                      boolean protect) throws ClosedException;

    void WriteMessage(@NonNull Closeable closeable, @NonNull PeerId peer,
                      @NonNull BitSwapMessage message, int timeout)
            throws ClosedException, ProtocolNotSupported, ConnectionNotSupported;

    @NonNull
    Set<PeerId> getPeers();
}
