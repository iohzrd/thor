package threads.thor.bt.torrent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import threads.thor.bt.BtException;
import threads.thor.bt.IConsumers;
import threads.thor.bt.IProduces;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.Peer;
import threads.thor.bt.protocol.InvalidMessageException;
import threads.thor.bt.protocol.Message;
import threads.thor.bt.protocol.Piece;
import threads.thor.bt.protocol.Request;

public class PeerRequestConsumer implements IProduces, IConsumers {

    private final TorrentId torrentId;
    private final DataWorker dataWorker;
    private final Map<Peer, Queue<BlockRead>> completedRequests;

    public PeerRequestConsumer(TorrentId torrentId, DataWorker dataWorker) {
        this.torrentId = torrentId;
        this.dataWorker = dataWorker;
        this.completedRequests = new ConcurrentHashMap<>();
    }

    @Override
    public void doConsume(Message message, MessageContext messageContext) {
        if (message instanceof Request) {
            consume((Request) message, messageContext);
        }
    }

    @Override
    public List<MessageConsumer<? extends Message>> getConsumers() {
        List<MessageConsumer<? extends Message>> list = new ArrayList<>();
        list.add(new MessageConsumer<Request>() {
            @Override
            public Class<Request> getConsumedType() {
                return Request.class;
            }

            @Override
            public void consume(Request message, MessageContext context) {
                doConsume(message, context);
            }
        });

        return list;
    }


    private void consume(Request request, MessageContext context) {
        ConnectionState connectionState = context.getConnectionState();
        if (!connectionState.isChoking()) {
            addBlockRequest(context.getPeer(), request).whenComplete((block, error) -> {
                if (error != null) {

                } else if (block.getError().isPresent()) {

                } else if (block.isRejected()) {

                } else {
                    getCompletedRequestsForPeer(context.getPeer()).add(block);
                }
            });
        }
    }

    private CompletableFuture<BlockRead> addBlockRequest(Peer peer, Request request) {
        return dataWorker.addBlockRequest(torrentId, peer, request.getPieceIndex(), request.getOffset(), request.getLength());
    }

    private Queue<BlockRead> getCompletedRequestsForPeer(Peer peer) {
        Queue<BlockRead> queue = completedRequests.get(peer);
        if (queue == null) {
            queue = new ConcurrentLinkedQueue<>();
            Queue<BlockRead> existing = completedRequests.putIfAbsent(peer, queue);
            if (existing != null) {
                queue = existing;
            }
        }
        return queue;
    }


    public void produce(Consumer<Message> messageConsumer, MessageContext context) {
        Peer peer = context.getPeer();
        Queue<BlockRead> queue = getCompletedRequestsForPeer(peer);
        BlockRead block;
        while ((block = queue.poll()) != null) {
            try {
                messageConsumer.accept(new Piece(block.getPieceIndex(), block.getOffset(),
                        block.getLength(), block.getReader().get()));
            } catch (InvalidMessageException e) {
                throw new BtException("Failed to send PIECE", e);
            }
        }
    }
}
