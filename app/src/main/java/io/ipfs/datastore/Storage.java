package io.ipfs.datastore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


public interface Storage {
    void insertBlock(@NonNull String id, @NonNull byte[] bytes);

    @Nullable
    BlockData getBlock(@NonNull String id);

    void deleteBlock(@NonNull String id);

    int sizeBlock(@NonNull String id);

    boolean hasBlock(@NonNull String id);
}
