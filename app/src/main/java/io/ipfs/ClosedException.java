package io.ipfs;

public class ClosedException extends Exception {
    public ClosedException(){
        super("Context closed");
    }
}
