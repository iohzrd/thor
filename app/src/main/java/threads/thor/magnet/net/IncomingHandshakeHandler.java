package threads.thor.magnet.net;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

import threads.thor.Settings;
import threads.thor.magnet.metainfo.TorrentId;
import threads.thor.magnet.protocol.Handshake;
import threads.thor.magnet.protocol.HandshakeFactory;
import threads.thor.magnet.protocol.Message;
import threads.thor.magnet.torrent.TorrentDescriptor;
import threads.thor.magnet.torrent.TorrentRegistry;

class IncomingHandshakeHandler implements ConnectionHandler {

    private final HandshakeFactory handshakeFactory;
    private final TorrentRegistry torrentRegistry;
    private final Collection<HandshakeHandler> handshakeHandlers;


    IncomingHandshakeHandler(HandshakeFactory handshakeFactory, TorrentRegistry torrentRegistry,
                             Collection<HandshakeHandler> handshakeHandlers) {
        this.handshakeFactory = handshakeFactory;
        this.torrentRegistry = torrentRegistry;
        this.handshakeHandlers = handshakeHandlers;
    }

    @Override
    public boolean handleConnection(PeerConnection connection) {

        Message firstMessage = null;
        try {
            firstMessage = connection.readMessage(Settings.peerHandshakeTimeout.toMillis());
        } catch (Throwable ignored) {
            // ignore exception
        }

        if (firstMessage != null) {
            if (Handshake.class.equals(firstMessage.getClass())) {

                Handshake peerHandshake = (Handshake) firstMessage;
                TorrentId torrentId = peerHandshake.getTorrentId();
                Optional<TorrentDescriptor> descriptorOptional = torrentRegistry.getDescriptor(torrentId);
                // it's OK if descriptor is not present -- threads.torrent might be being fetched at the time
                if (torrentRegistry.getTorrentIds().contains(torrentId)
                        && (!descriptorOptional.isPresent() || descriptorOptional.get().isActive())) {

                    Handshake handshake = handshakeFactory.createHandshake(torrentId);
                    handshakeHandlers.forEach(handler ->
                            handler.processOutgoingHandshake(handshake));

                    try {
                        connection.postMessage(handshake);
                    } catch (IOException e) {

                        return false;
                    }
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
