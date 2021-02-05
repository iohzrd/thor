package threads.thor.magnet.net.extended;

import java.io.IOException;

import threads.thor.magnet.metainfo.TorrentId;
import threads.thor.magnet.net.HandshakeHandler;
import threads.thor.magnet.net.PeerConnection;
import threads.thor.magnet.protocol.Handshake;
import threads.thor.magnet.protocol.extended.ExtendedHandshake;
import threads.thor.magnet.protocol.extended.ExtendedHandshakeFactory;

public class ExtendedProtocolHandshakeHandler implements HandshakeHandler {

    private static final int EXTENDED_FLAG_BIT_INDEX = 43;

    private final ExtendedHandshakeFactory extendedHandshakeFactory;

    public ExtendedProtocolHandshakeHandler(ExtendedHandshakeFactory extendedHandshakeFactory) {
        this.extendedHandshakeFactory = extendedHandshakeFactory;
    }

    @Override
    public void processIncomingHandshake(PeerConnection connection, Handshake peerHandshake) {
        ExtendedHandshake extendedHandshake = getHandshake(peerHandshake.getTorrentId());
        // do not send the extended handshake
        // if local client does not have any extensions turned on
        if (!extendedHandshake.getData().isEmpty()) {
            try {
                connection.postMessage(extendedHandshake);
            } catch (IOException e) {
                throw new RuntimeException("Failed to send extended handshake to peer: " + connection.getRemotePeer(), e);
            }
        }
    }

    @Override
    public void processOutgoingHandshake(Handshake handshake) {
        ExtendedHandshake extendedHandshake = getHandshake(handshake.getTorrentId());
        // do not advertise support for the extended protocol
        // if local client does not have any extensions turned on
        if (!extendedHandshake.getData().isEmpty()) {
            handshake.setReservedBit(EXTENDED_FLAG_BIT_INDEX);
        }
    }

    private ExtendedHandshake getHandshake(TorrentId torrentId) {
        return extendedHandshakeFactory.getHandshake(torrentId);
    }
}