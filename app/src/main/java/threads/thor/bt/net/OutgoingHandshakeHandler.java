package threads.thor.bt.net;

import java.io.IOException;
import java.util.Collection;

import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.protocol.Handshake;
import threads.thor.bt.protocol.HandshakeFactory;
import threads.thor.bt.protocol.Message;

class OutgoingHandshakeHandler implements ConnectionHandler {


    private final HandshakeFactory handshakeFactory;
    private final TorrentId torrentId;
    private final Collection<HandshakeHandler> handshakeHandlers;
    private final long handshakeTimeout;

    OutgoingHandshakeHandler(HandshakeFactory handshakeFactory, TorrentId torrentId,
                             Collection<HandshakeHandler> handshakeHandlers, long handshakeTimeout) {
        this.handshakeFactory = handshakeFactory;
        this.torrentId = torrentId;
        this.handshakeHandlers = handshakeHandlers;
        this.handshakeTimeout = handshakeTimeout;
    }

    @Override
    public boolean handleConnection(PeerConnection connection) {
        Peer peer = connection.getRemotePeer();

        Handshake handshake = handshakeFactory.createHandshake(torrentId);
        handshakeHandlers.forEach(handler ->
                handler.processOutgoingHandshake(handshake));
        try {
            connection.postMessage(handshake);
        } catch (IOException e) {

            return false;
        }

        Message firstMessage = null;
        try {
            firstMessage = connection.readMessage(handshakeTimeout);
        } catch (Throwable ignored) {
            // ignore exception
        }
        if (firstMessage != null) {
            if (Handshake.class.equals(firstMessage.getClass())) {
                Handshake peerHandshake = (Handshake) firstMessage;
                TorrentId incomingTorrentId = peerHandshake.getTorrentId();
                if (torrentId.equals(incomingTorrentId)) {
                    connection.setTorrentId(torrentId);

                    handshakeHandlers.forEach(handler ->
                            handler.processIncomingHandshake(new WriteOnlyPeerConnection(connection), peerHandshake));

                    return true;
                }
            }
        }
        return false;
    }
}
