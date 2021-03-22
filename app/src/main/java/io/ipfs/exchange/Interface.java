package io.ipfs.exchange;

import androidx.annotation.NonNull;

import io.Closeable;
import io.ipfs.cid.Cid;

public interface Interface extends Fetcher {
    void reset();

    void load(@NonNull Closeable closeable, @NonNull Cid cid);
}
