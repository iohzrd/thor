package io.ipfs.dht;

import androidx.annotation.NonNull;

import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.host.PeerId;
import io.ipfs.ipns.Ipns;

public interface Routing {
    void putValue(@NonNull Closeable closable, @NonNull byte[] key,
                  @NonNull byte[] data) throws ClosedException;


    boolean findPeer(@NonNull Closeable closeable, @NonNull PeerId peerID) throws ClosedException;


    void searchValue(@NonNull Closeable closeable, @NonNull ResolveInfo resolveInfo,
                     @NonNull byte[] key, int quorum) throws ClosedException;


    void findProviders(@NonNull Closeable closeable, @NonNull Providers providers,
                       @NonNull Cid cid) throws ClosedException;

    void provide(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException;


    void bootstrap();


    interface Providers {
        void peer(@NonNull PeerId peerId) throws ClosedException;
    }

    interface ResolveInfo {
        void resolved(@NonNull Ipns.Entry entry);
    }
}
