package io.ipfs.format;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import io.ipfs.cid.Cid;
import io.core.Closeable;
import io.core.ClosedException;

public interface NodeGetter {
    @Nullable
    Node Get(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException;

    void Load(@NonNull Closeable ctx, @NonNull List<Cid> cids);
}
