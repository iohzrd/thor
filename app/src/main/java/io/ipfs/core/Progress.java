package io.ipfs.core;


public interface Progress extends Closeable {

    void setProgress(int progress);

    boolean doProgress();

}
