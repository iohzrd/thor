package io.ipfs.utils;

import io.ipfs.format.Reader;

public interface Splitter {
    Reader Reader();

    byte[] NextBytes();

    boolean Done();
}
