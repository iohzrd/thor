package io.dht;

import androidx.annotation.NonNull;

import io.libp2p.core.PeerId;

public interface Channel {
     void invoke(@NonNull PeerId peerId);
}
