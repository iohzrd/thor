package io.ipfs.datastore;

import java.util.concurrent.ConcurrentHashMap;

import io.dht.Batching;

public class MapDataStore implements Batching {
    private final ConcurrentHashMap<Key, Data> values = new ConcurrentHashMap<>();

    private static class Data {
        public byte[] data;
    }
}
