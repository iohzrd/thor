package threads.thor.bt.net;

import java.nio.channels.SocketChannel;

import threads.thor.bt.metainfo.TorrentId;

public interface IPeerConnectionFactory {

    /**
     * @since 1.6
     */
    ConnectionResult createOutgoingConnection(Peer peer, TorrentId torrentId);

    /**
     * @since 1.6
     */
    ConnectionResult createIncomingConnection(Peer peer, SocketChannel channel);
}
