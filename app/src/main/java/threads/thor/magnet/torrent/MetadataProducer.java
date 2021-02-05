package threads.thor.magnet.torrent;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import threads.thor.Settings;
import threads.thor.magnet.IConsumers;
import threads.thor.magnet.IProduces;
import threads.thor.magnet.magnet.UtMetadata;
import threads.thor.magnet.metainfo.Torrent;
import threads.thor.magnet.net.Peer;
import threads.thor.magnet.protocol.Message;

public class MetadataProducer implements IProduces, IConsumers {

    private final Torrent torrentSupplier;
    private final ConcurrentMap<Peer, Queue<Message>> outboundMessages;
    private final int metadataExchangeBlockSize;
    // initialized on the first metadata request if the threads.torrent is present
    private volatile ExchangedMetadata metadata;

    public MetadataProducer(@Nullable Torrent torrentSupplier) {
        this.torrentSupplier = torrentSupplier;
        this.outboundMessages = new ConcurrentHashMap<>();
        this.metadataExchangeBlockSize = Settings.metadataExchangeBlockSize;
    }

    @Override
    public void doConsume(Message message, MessageContext messageContext) {
        if (message instanceof UtMetadata) {
            consume((UtMetadata) message, messageContext);
        }
    }

    @Override
    public List<MessageConsumer<? extends Message>> getConsumers() {
        List<MessageConsumer<? extends Message>> list = new ArrayList<>();
        list.add(new MessageConsumer<UtMetadata>() {
            @Override
            public Class<UtMetadata> getConsumedType() {
                return UtMetadata.class;
            }

            @Override
            public void consume(UtMetadata message, MessageContext context) {
                doConsume(message, context);
            }
        });
        return list;
    }

    private void consume(UtMetadata message, MessageContext context) {
        Peer peer = context.getPeer();
        // being lenient herer and not checking if the peer advertised ut_metadata support
        if (message.getType() == UtMetadata.Type.REQUEST) {// TODO: spam protection
            processMetadataRequest(peer, message.getPieceIndex());
        }// ignore
    }

    private void processMetadataRequest(Peer peer, int pieceIndex) {
        Message response;

        Torrent torrent = torrentSupplier;
        if (torrent == null || torrent.isPrivate()) {
            // reject all requests if:
            // - we don't have the torrent yet
            // - torrent is private
            response = UtMetadata.reject(pieceIndex);
        } else {
            if (metadata == null) {
                metadata = new ExchangedMetadata(torrent.getSource().getExchangedMetadata(), metadataExchangeBlockSize);
            }

            response = UtMetadata.data(pieceIndex, metadata.length(), metadata.getBlock(pieceIndex));
        }

        getOrCreateOutboundMessages(peer).add(response);
    }

    private Queue<Message> getOrCreateOutboundMessages(Peer peer) {
        Queue<Message> queue = outboundMessages.get(peer);
        if (queue == null) {
            queue = new LinkedBlockingQueue<>();
            Queue<Message> existing = outboundMessages.putIfAbsent(peer, queue);
            if (existing != null) {
                queue = existing;
            }
        }
        return queue;
    }


    public void produce(Consumer<Message> messageConsumer, MessageContext context) {
        Peer peer = context.getPeer();

        Queue<Message> queue = outboundMessages.get(peer);
        if (queue != null && queue.size() > 0) {
            messageConsumer.accept(queue.poll());
        }
    }
}
