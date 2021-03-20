package io.ipfs.format;

public interface Metrics {
    void leeching(int amount);

    void seeding(int amount);
}
