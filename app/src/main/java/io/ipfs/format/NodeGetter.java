package io.ipfs.format;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import io.Closeable;
import io.ipfs.cid.Cid;

public interface NodeGetter {
    @Nullable
    Node Get(@NonNull Closeable closeable, @NonNull Cid cid);

    void Load(@NonNull Closeable ctx, @NonNull List<Cid> cids);
}
