/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package threads.thor.bt.torrent.messaging;

import java.util.Deque;
import java.util.concurrent.LinkedBlockingDeque;

import threads.thor.bt.net.ConnectionKey;
import threads.thor.bt.protocol.Cancel;
import threads.thor.bt.protocol.Choke;
import threads.thor.bt.protocol.Interested;
import threads.thor.bt.protocol.Message;
import threads.thor.bt.protocol.NotInterested;
import threads.thor.bt.protocol.Piece;
import threads.thor.bt.protocol.Unchoke;

/**
 * Messaging interface, that encapsulates all messaging agents (consumers and producers)
 * for a particular threads.torrent processing session.
 * Routes incoming messages directly to interested consumers, based on message types.
 *
 * @since 1.0
 */
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
            } else {
                connectionState.incrementUploaded(piece.getLength());
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
