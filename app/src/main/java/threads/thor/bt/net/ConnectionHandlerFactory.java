package threads.thor.bt.net;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.protocol.HandshakeFactory;
import threads.thor.bt.torrent.TorrentRegistry;

public class ConnectionHandlerFactory {

    private final HandshakeFactory handshakeFactory;
    private final ConnectionHandler incomingHandler;
    private final Duration peerHandshakeTimeout;

    private final Collection<HandshakeHandler> handshakeHandlers;

    private final Map<TorrentId, ConnectionHandler> outgoingHandlers;

    public ConnectionHandlerFactory(HandshakeFactory handshakeFactory,
                                    TorrentRegistry torrentRegistry,
                                    Collection<HandshakeHandler> handshakeHandlers,
                                    Duration peerHandshakeTimeout) {
        this.handshakeFactory = handshakeFactory;
        this.incomingHandler = new IncomingHandshakeHandler(handshakeFactory, torrentRegistry,
                handshakeHandlers, peerHandshakeTimeout);

        this.outgoingHandlers = new ConcurrentHashMap<>();
        this.handshakeHandlers = handshakeHandlers;

        this.peerHandshakeTimeout = peerHandshakeTimeout;
    }

    public ConnectionHandler getIncomingHandler() {
        return incomingHandler;
    }

    public ConnectionHandler getOutgoingHandler(TorrentId torrentId) {
        Objects.requireNonNull(torrentId, "Missing threads.torrent ID");
        ConnectionHandler outgoing = outgoingHandlers.get(torrentId);
        if (outgoing == null) {
            outgoing = new OutgoingHandshakeHandler(handshakeFactory, torrentId,
                    handshakeHandlers, peerHandshakeTimeout.toMillis());
            ConnectionHandler existing = outgoingHandlers.putIfAbsent(torrentId, outgoing);
            if (existing != null) {
                outgoing = existing;
            }
        }
        return outgoing;
    }
}
