package threads.lite.dag;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import threads.lite.cid.Cid;
import threads.lite.core.Closeable;
import threads.lite.core.ClosedException;
import threads.lite.format.Block;

public interface BlockGetter {
    @Nullable
    Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid, boolean root) throws ClosedException;

    void addBlock(@NonNull Block block);

    void preload(@NonNull Closeable closeable, @NonNull List<Cid> cids);
}
