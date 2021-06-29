package threads.lite.dht;

import androidx.annotation.NonNull;

import threads.lite.cid.Cid;
import threads.lite.cid.PeerId;
import threads.lite.core.Closeable;
import threads.lite.ipns.Ipns;

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
