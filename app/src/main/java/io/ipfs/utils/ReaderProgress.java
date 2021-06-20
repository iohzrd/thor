package io.ipfs.utils;

import io.ipfs.core.Progress;

public interface ReaderProgress extends Progress {
    long getSize();
}
