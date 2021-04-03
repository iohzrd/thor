package io.dht;

public class Offline extends Option {
    private final boolean offline;

    public Offline(boolean offline) {
        this.offline = offline;
    }

    public boolean isOffline() {
        return offline;
    }
}
