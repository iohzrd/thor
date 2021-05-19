package io.ipfs.exchange;

import androidx.annotation.NonNull;

import java.util.List;

import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.format.Block;

public interface Fetcher {
    Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid, boolean root) throws ClosedException;

    void preload(@NonNull Closeable closeable, @NonNull List<Cid> cids);
}
