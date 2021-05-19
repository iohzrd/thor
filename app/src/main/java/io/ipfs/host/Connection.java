package io.ipfs.host;


import io.ipfs.multiaddr.Multiaddr;
import io.netty.incubator.codec.quic.QuicChannel;

public interface Connection {
    PeerId remoteId();

    Multiaddr remoteAddress();

    QuicChannel channel();

    void disconnect();

}
