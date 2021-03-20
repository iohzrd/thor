package io.ipfs.utils;

public class TimeoutProgress implements Progress {
    private final long timeout;
    private final long start;

    public TimeoutProgress(long timeout) {
        this.timeout = timeout;
        this.start = System.currentTimeMillis();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void setProgress(int percent) {

    }

    @Override
    public boolean doProgress() {
        return false;
    }

    @Override
    public boolean isClosed() {
        return (System.currentTimeMillis() - start) > (timeout * 1000);
    }

}
