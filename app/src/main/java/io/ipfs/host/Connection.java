package io.ipfs.host;


import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.netty.channel.ChannelFuture;
import io.netty.incubator.codec.quic.QuicChannel;

public interface Connection {
    PeerId remoteId();

    ChannelFuture close();

    Multiaddr remoteAddress();

    QuicChannel channel();

}
