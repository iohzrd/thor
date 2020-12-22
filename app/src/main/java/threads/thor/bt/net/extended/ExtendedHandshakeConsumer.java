package threads.thor.bt.net.extended;

import java.util.ArrayList;
import java.util.List;

import threads.thor.bt.IConsumers;
import threads.thor.bt.bencoding.BEInteger;
import threads.thor.bt.net.InetPeer;
import threads.thor.bt.net.PeerConnectionPool;
import threads.thor.bt.protocol.Message;
import threads.thor.bt.protocol.extended.ExtendedHandshake;
import threads.thor.bt.torrent.MessageConsumer;
import threads.thor.bt.torrent.MessageContext;

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
