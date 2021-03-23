package io.ipfs.format;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import io.Closeable;
import io.ipfs.ClosedException;
import io.ipfs.cid.Cid;

public interface NodeGetter {
    @Nullable
    Node Get(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException;

    void Load(@NonNull Closeable ctx, @NonNull List<Cid> cids);
}
