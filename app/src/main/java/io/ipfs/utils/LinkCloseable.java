package io.ipfs.utils;

import androidx.annotation.NonNull;

import io.core.Closeable;

public interface LinkCloseable extends Closeable {
    void info(@NonNull Link link);
}
