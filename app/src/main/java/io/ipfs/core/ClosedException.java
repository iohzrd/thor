package io.ipfs.core;

public class ClosedException extends Exception {
    public ClosedException() {
        super("Context closed");
    }
}