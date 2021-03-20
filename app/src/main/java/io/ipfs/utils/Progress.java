package io.ipfs.utils;

import io.Closeable;

public interface Progress extends Closeable {

    void setProgress(int progress);

    boolean doProgress();

}
