package threads.lite.format;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import threads.lite.cid.Cid;
import threads.lite.core.Closeable;
import threads.lite.core.ClosedException;

public interface NodeGetter {
    @Nullable
    Node getNode(@NonNull Closeable closeable, @NonNull Cid cid, boolean root) throws ClosedException;

    void preload(@NonNull Closeable ctx, @NonNull List<Cid> cids);
}
