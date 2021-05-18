package io.ipfs.utils;

import io.ipfs.core.Closeable;

public interface Progress extends Closeable {

    void setProgress(int progress);

    boolean doProgress();

}
