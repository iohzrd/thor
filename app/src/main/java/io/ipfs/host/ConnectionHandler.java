package io.ipfs.host;

import androidx.annotation.NonNull;

import io.ipfs.cid.PeerId;

public interface ConnectionHandler {
    void connected(@NonNull PeerId peerId);
}
