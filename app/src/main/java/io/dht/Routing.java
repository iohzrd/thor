package io.dht;

import androidx.annotation.NonNull;

import io.core.Closeable;
import io.core.ClosedException;
import io.ipfs.cid.Cid;
import io.libp2p.AddrInfo;
import io.libp2p.core.PeerId;

public interface Routing {
    void PutValue(@NonNull Closeable closable, @NonNull byte[] key,
                  @NonNull byte[] data) throws ClosedException;


    AddrInfo FindPeer(@NonNull Closeable closeable, @NonNull PeerId peerID) throws ClosedException;


    void SearchValue(@NonNull Closeable closeable, @NonNull ResolveInfo resolveInfo,
                     @NonNull byte[] key, int quorum) throws ClosedException;


    void FindProviders(@NonNull Closeable closeable, @NonNull Channel channel,
                       @NonNull Cid cid) throws ClosedException;

    void Provide(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException;


    void init();
}
