package threads.thor.magnet.net.extended;

import java.util.ArrayList;
import java.util.List;

import threads.thor.magnet.IConsumers;
import threads.thor.magnet.bencoding.BEInteger;
import threads.thor.magnet.net.InetPeer;
import threads.thor.magnet.net.PeerConnectionPool;
import threads.thor.magnet.protocol.Message;
import threads.thor.magnet.protocol.extended.ExtendedHandshake;
import threads.thor.magnet.torrent.MessageConsumer;
import threads.thor.magnet.torrent.MessageContext;

public class ExtendedHandshakeConsumer implements IConsumers {

    private final PeerConnectionPool connectionPool;

    public ExtendedHandshakeConsumer(PeerConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    @Override
    public void doConsume(Message message, MessageContext messageContext) {
        if (message instanceof ExtendedHandshake) {
            consume((ExtendedHandshake) message, messageContext);
        }
    }

    @Override
    public List<MessageConsumer<? extends Message>> getConsumers() {
        List<MessageConsumer<? extends Message>> list = new ArrayList<>();
        list.add(new MessageConsumer<ExtendedHandshake>() {
            @Override
            public Class<ExtendedHandshake> getConsumedType() {
                return ExtendedHandshake.class;
            }

            @Override
            public void consume(ExtendedHandshake message, MessageContext context) {
                doConsume(message, context);
            }
        });
        return list;
    }

    private void consume(ExtendedHandshake message, MessageContext messageContext) {
        BEInteger peerListeningPort = message.getPort();
        if (peerListeningPort != null) {
            InetPeer peer = (InetPeer) messageContext.getConnectionKey().getPeer();
            int listeningPort = peerListeningPort.getValue().intValue();
            peer.setPort(listeningPort);

            connectionPool.checkDuplicateConnections(messageContext.getConnectionKey().getTorrentId(), peer);
        }
    }
}
