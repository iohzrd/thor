package io.ipfs.host;


import net.luminis.quic.QuicClientConnection;

import io.ipfs.cid.Multiaddr;
import io.ipfs.cid.PeerId;

public interface Connection {
    PeerId remoteId();

    Multiaddr remoteAddress();

    QuicClientConnection channel();

    void disconnect();

    boolean isConnected();
}
