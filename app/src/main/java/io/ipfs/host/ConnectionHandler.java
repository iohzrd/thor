package io.ipfs.host;

import androidx.annotation.NonNull;

public interface ConnectionHandler {
    void handleConnection(@NonNull Connection connection);
}
