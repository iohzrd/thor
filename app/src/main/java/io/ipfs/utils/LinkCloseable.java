package io.ipfs.utils;

import androidx.annotation.NonNull;

import io.ipfs.core.Closeable;

public interface LinkCloseable extends Closeable {
    void info(@NonNull Link link);
}
