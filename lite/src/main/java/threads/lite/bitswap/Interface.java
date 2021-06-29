package threads.lite.bitswap;


import androidx.annotation.NonNull;

import java.util.List;

import threads.lite.cid.Cid;
import threads.lite.core.Closeable;
import threads.lite.core.ClosedException;
import threads.lite.format.Block;

public interface Interface {
    void reset();

    Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid, boolean root) throws ClosedException;

    void preload(@NonNull Closeable closeable, @NonNull List<Cid> cids);
}
