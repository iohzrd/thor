package io.ipfs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import threads.thor.core.blocks.Block;

public interface Storage {
    void insertBlock(@NonNull String id, @NonNull byte[] bytes);

    @Nullable
    Block getBlock(@NonNull String id);

    void deleteBlock(@NonNull String id);

    int sizeBlock(@NonNull String id);

    boolean hasBlock(@NonNull String id);
}
