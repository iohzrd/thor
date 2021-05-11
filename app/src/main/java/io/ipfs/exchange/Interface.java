package io.ipfs.exchange;

import androidx.annotation.NonNull;

import io.core.Closeable;
import io.ipfs.bitswap.BitSwapReceiver;
import io.ipfs.cid.Cid;

public interface Interface extends Fetcher, BitSwapReceiver {
    void reset();

    void load(@NonNull Closeable closeable, @NonNull Cid cid);
}
