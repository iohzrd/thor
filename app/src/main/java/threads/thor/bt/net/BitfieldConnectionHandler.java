package threads.thor.bt.net;

import java.io.IOException;
import java.util.Optional;

import threads.thor.bt.data.Bitfield;
import threads.thor.bt.protocol.BitOrder;
import threads.thor.bt.protocol.Handshake;
import threads.thor.bt.torrent.TorrentDescriptor;
import threads.thor.bt.torrent.TorrentRegistry;

public class BitfieldConnectionHandler implements HandshakeHandler {

    private final TorrentRegistry torrentRegistry;


    public BitfieldConnectionHandler(TorrentRegistry torrentRegistry) {
        this.torrentRegistry = torrentRegistry;
    }

    @Override
    public void processIncomingHandshake(PeerConnection connection, Handshake peerHandshake) {
        Optional<TorrentDescriptor> descriptorOptional = torrentRegistry.getDescriptor(connection.getTorrentId());
        if (descriptorOptional.isPresent() && descriptorOptional.get().isActive()
                && descriptorOptional.get().getDataDescriptor() != null) {
            Bitfield bitfield = descriptorOptional.get().getDataDescriptor().getBitfield();

            if (bitfield.getPiecesComplete() > 0) {
                Peer peer = connection.getRemotePeer();
                threads.thor.bt.protocol.Bitfield bitfieldMessage = new threads.thor.bt.protocol.Bitfield(bitfield.toByteArray(BitOrder.LITTLE_ENDIAN));
                try {
                    connection.postMessage(bitfieldMessage);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to send bitfield to peer: " + peer, e);
                }
            }
        }
    }

    @Override
    public void processOutgoingHandshake(Handshake handshake) {
        // do nothing
    }
}
