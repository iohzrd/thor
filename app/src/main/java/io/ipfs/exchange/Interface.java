package io.ipfs.exchange;

import androidx.annotation.NonNull;

import io.Closeable;
import io.ipfs.bitswap.Receiver;
import io.ipfs.cid.Cid;

public interface Interface extends Fetcher, Receiver {
    void reset();

    void load(@NonNull Closeable closeable, @NonNull Cid cid);
}
