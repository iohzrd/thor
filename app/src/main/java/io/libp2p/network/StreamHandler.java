package io.libp2p.network;

import androidx.annotation.NonNull;

public interface StreamHandler {
    void handle(@NonNull Stream stream);
}
