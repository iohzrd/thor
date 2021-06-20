package io.ipfs.dht;

import androidx.annotation.NonNull;

import io.ipfs.cid.Cid;
import io.ipfs.cid.PeerId;
import io.ipfs.core.Closeable;
import io.ipfs.ipns.Ipns;

public interface Routing {
    void putValue(@NonNull Closeable closable, @NonNull byte[] key, @NonNull byte[] data);


    void findPeer(@NonNull Closeable closeable, @NonNull Updater updater, @NonNull PeerId peerID);


    void searchValue(@NonNull Closeable closeable, @NonNull ResolveInfo resolveInfo,
                     @NonNull byte[] key);


    void findProviders(@NonNull Closeable closeable, @NonNull Providers providers, @NonNull Cid cid);

    void provide(@NonNull Closeable closeable, @NonNull Cid cid);


    void bootstrap();


    interface Providers {
        void peer(@NonNull PeerId peerId);
    }

    interface Updater {
        void peer(@NonNull PeerId peerId);
    }

    interface ResolveInfo {
        void resolved(@NonNull Ipns.Entry entry);
    }

}
