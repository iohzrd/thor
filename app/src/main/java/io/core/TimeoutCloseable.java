package io.core;

public class TimeoutCloseable implements Closeable {
    private final long timeout;
    private final long start;

    public TimeoutCloseable(long timeout) {
        this.timeout = timeout;
        this.start = System.currentTimeMillis();
    }

    @Override
    public boolean isClosed() {
        return (System.currentTimeMillis() - start) > (timeout * 1000);
    }

}