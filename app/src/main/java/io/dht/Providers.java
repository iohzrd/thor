package io.dht;

import androidx.annotation.NonNull;

import io.Closeable;

public interface Providers extends Closeable {
    void Peer(@NonNull String peerID);
}
