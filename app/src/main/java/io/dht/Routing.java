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

    // FindPeer searches for a peer with given ID, returns a peer.AddrInfo
    // with relevant addresses.
    AddrInfo FindPeer(@NonNull Closeable closeable, @NonNull PeerId peerID) throws ClosedException;

    // SearchValue searches for better and better values from this value
    // store corresponding to the given Key. By default implementations must
    // stop the search after a good value is found. A 'good' value is a value
    // that would be returned from GetValue.
    //
    // Useful when you want a result *now* but still want to hear about
    // better/newer results.
    //
    // Implementations of this methods won't return ErrNotFound. When a value
    // couldn't be found, the channel will get closed without passing any results
    void SearchValue(@NonNull Closeable closeable, @NonNull ResolveInfo resolveInfo,
                     @NonNull byte[] key, Option... options) throws ClosedException;


    void FindProvidersAsync(@NonNull Closeable closeable, @NonNull Providers providers,
                            @NonNull Cid cid) throws ClosedException;

    void Provide(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException;


    void init();
}
