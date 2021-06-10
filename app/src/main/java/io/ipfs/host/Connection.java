package io.ipfs.host;


import net.luminis.quic.QuicClientConnection;

import io.ipfs.multiaddr.Multiaddr;

public interface Connection {
    PeerId remoteId();

    Multiaddr remoteAddress();

    QuicClientConnection channel();

    void disconnect();

}
