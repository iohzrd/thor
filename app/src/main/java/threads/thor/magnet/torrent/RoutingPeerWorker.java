package threads.thor.magnet.torrent;

import java.util.Deque;
import java.util.concurrent.LinkedBlockingDeque;

import threads.thor.magnet.net.ConnectionKey;
import threads.thor.magnet.protocol.Cancel;
import threads.thor.magnet.protocol.Choke;
import threads.thor.magnet.protocol.Interested;
import threads.thor.magnet.protocol.Message;
import threads.thor.magnet.protocol.NotInterested;
import threads.thor.magnet.protocol.Piece;
import threads.thor.magnet.protocol.Unchoke;

class RoutingPeerWorker implements PeerWorker {

    private final ConnectionState connectionState;

    private final MessageRouter router;
    private final MessageContext context;

    private final Deque<Message> outgoingMessages;

    private final Choker choker;

    public RoutingPeerWorker(ConnectionKey connectionKey, MessageRouter router) {
        this.connectionState = new ConnectionState();
        this.router = router;
        this.context = new MessageContext(connectionKey, connectionState);
        this.outgoingMessages = new LinkedBlockingDeque<>();
        this.choker = Choker.choker();
    }

    @Override
    public ConnectionState getConnectionState() {
        return connectionState;
    }

    @Override
    public void accept(Message message) {
        router.consume(message, context);
        updateConnection();
    }

    private void postMessage(Message message) {
        if (isUrgent(message)) {
            addUrgent(message);
        } else {
            add(message);
        }
    }

    private boolean isUrgent(Message message) {
        // TODO: this should be done based on priorities
        Class<? extends Message> messageType = message.getClass();
        return Choke.class.equals(messageType) || Unchoke.class.equals(messageType) || Cancel.class.equals(messageType);
    }

    private void add(Message message) {
        outgoingMessages.add(message);
    }

    private void addUrgent(Message message) {
        outgoingMessages.addFirst(message);
    }

    @Override
    public Message get() {
        if (outgoingMessages.isEmpty()) {
            router.produce(this::postMessage, context);
            updateConnection();
        }
        return postProcessOutgoingMessage(outgoingMessages.poll());
    }

    private Message postProcessOutgoingMessage(Message message) {

        if (message == null) {
            return null;
        }

        Class<? extends Message> messageType = message.getClass();

        if (Piece.class.equals(messageType)) {
            Piece piece = (Piece) message;
            // check that peer hadn't sent cancel while we were preparing the requested block
            if (isCancelled(piece)) {
                // dispose of message
                return null;
            }
        }
        if (Interested.class.equals(messageType)) {
            connectionState.setInterested(true);
        }
        if (NotInterested.class.equals(messageType)) {
            connectionState.setInterested(false);
        }
        if (Choke.class.equals(messageType)) {
            connectionState.setShouldChoke(true);
        }
        if (Unchoke.class.equals(messageType)) {
            connectionState.setShouldChoke(false);
        }

        return message;
    }

    private boolean isCancelled(Piece piece) {

        int pieceIndex = piece.getPieceIndex(),
                offset = piece.getOffset(),
                length = piece.getLength();

        return connectionState.getCancelledPeerRequests().remove(Mapper.mapper().buildKey(pieceIndex, offset, length));
    }

    private void updateConnection() {
        choker.handleConnection(connectionState, this::postMessage);
    }
}
