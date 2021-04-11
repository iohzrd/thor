package io.core;


public class ConnectionFailure extends Exception {
    public ConnectionFailure(){
        super("Connection not supported");
    }
}
