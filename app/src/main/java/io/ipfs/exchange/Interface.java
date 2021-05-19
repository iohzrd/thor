package io.ipfs.exchange;

import androidx.annotation.NonNull;

import io.ipfs.bitswap.BitSwapReceiver;
import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;

public interface Interface extends Fetcher, BitSwapReceiver {
    void reset();

    void loadProvider(@NonNull Closeable closeable, @NonNull Cid cid);

}
