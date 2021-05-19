package io.ipfs.host;


import io.ipfs.multiaddr.Multiaddr;
import io.netty.channel.ChannelFuture;
import io.netty.incubator.codec.quic.QuicChannel;

public interface Connection {
    PeerId remoteId();

    ChannelFuture close();

    Multiaddr remoteAddress();

    QuicChannel channel();

    void disconnect();

}
