package io.dht;

public class QueryKey {

    // Space is the KeySpace this Key is related to.
    KeySpace Space;

    // Original is the original value of the identifier
    byte[] Original;

    // Bytes is the new value of the identifier, in the KeySpace.
    byte[] Bytes;
}
