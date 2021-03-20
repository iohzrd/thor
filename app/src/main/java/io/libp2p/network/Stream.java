package io.libp2p.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.libp2p.peer.PeerID;
import io.libp2p.protocol.Protocol;

public interface Stream {

    @NonNull
    Protocol Protocol();

    @NonNull
    PeerID RemotePeer();

    byte[] GetData();

    @Nullable
    String GetError();

}
