package io.ipfs.exchange;

import androidx.annotation.NonNull;

import java.util.List;

import io.ipfs.cid.Cid;
import io.core.Closeable;
import io.core.ClosedException;
import io.ipfs.format.Block;

public interface Fetcher {
    Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException;

    void loadBlocks(@NonNull Closeable closeable, @NonNull List<Cid> cids);
}
