package io.ipfs.exchange;

import androidx.annotation.NonNull;

import io.Closeable;
import io.ipfs.cid.Cid;
import io.ipfs.format.Block;

public interface Fetcher {
    Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid);
}
