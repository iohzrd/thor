package io.dht;

import androidx.annotation.NonNull;

import io.core.Closeable;
import io.core.ClosedException;
import io.ipfs.cid.Cid;

public interface ContentRouting {
    void FindProvidersAsync(@NonNull Providers providers, @NonNull Cid cid, int number) throws ClosedException;

    int Provide(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException;
}
