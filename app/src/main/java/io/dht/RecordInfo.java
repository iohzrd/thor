package io.dht;

import androidx.annotation.NonNull;

import io.libp2p.core.PeerId;

// todo simpler structure
public class RecordInfo {
    byte[] Val;
    PeerId From;

    public RecordInfo(@NonNull PeerId from, @NonNull byte[] data) {
        this.From = from;
        this.Val = data;
    }
}
