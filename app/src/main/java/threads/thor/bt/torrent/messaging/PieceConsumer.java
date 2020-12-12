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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import threads.thor.bt.IConsumers;
import threads.thor.bt.IProduces;
import threads.thor.bt.data.Bitfield;
import threads.thor.bt.event.EventSink;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.Peer;
import threads.thor.bt.net.buffer.BufferedData;
import threads.thor.bt.net.pipeline.IBufferedPieceRegistry;
import threads.thor.bt.protocol.Have;
import threads.thor.bt.protocol.Message;
import threads.thor.bt.protocol.Piece;
import threads.thor.bt.torrent.data.BlockWrite;
import threads.thor.bt.torrent.data.DataWorker;

/**
 * Consumes blocks, received from the remote peer.
 *
 * @since 1.0
 */
public class PieceConsumer implements IProduces, IConsumers {


    private final TorrentId torrentId;
    private final Bitfield bitfield;
    private final DataWorker dataWorker;
    private final IBufferedPieceRegistry bufferedPieceRegistry;
    private final EventSink eventSink;
    private final Queue<Integer> completedPieces;

    public PieceConsumer(TorrentId torrentId,
                         Bitfield bitfield,
                         DataWorker dataWorker,
                         IBufferedPieceRegistry bufferedPieceRegistry,
                         EventSink eventSink) {
        this.torrentId = torrentId;
        this.bitfield = bitfield;
        this.dataWorker = dataWorker;
        this.bufferedPieceRegistry = bufferedPieceRegistry;
        this.eventSink = eventSink;
        this.completedPieces = new LinkedBlockingQueue<>();
    }

    @Override
    public void doConsume(Message message, MessageContext messageContext) {
        if (message instanceof Piece) {
            consume((Piece) message, messageContext);
        }
    }

    @Override
    public List<MessageConsumer<? extends Message>> getConsumers() {
        List<MessageConsumer<? extends Message>> list = new ArrayList<>();
        list.add(new MessageConsumer<Piece>() {
            @Override
            public Class<Piece> getConsumedType() {
                return Piece.class;
            }

            @Override
            public void consume(Piece message, MessageContext context) {
                doConsume(message, context);
            }
        });


        return list;
    }


    private void consume(Piece piece, MessageContext context) {
        Peer peer = context.getPeer();
        ConnectionState connectionState = context.getConnectionState();

        // check that this block was requested in the first place
        if (!checkBlockIsExpected(connectionState, piece)) {

            disposeOfBlock(piece);
            return;
        }

        // discard blocks for pieces that have already been verified
        if (bitfield.isComplete(piece.getPieceIndex())) {
            disposeOfBlock(piece);

            return;
        }

        CompletableFuture<BlockWrite> future = addBlock(peer, connectionState, piece);
        if (future == null) {
            disposeOfBlock(piece);
        } else {
            future.whenComplete((block, error) -> {

                if (block.isRejected()) {

                } else {
                    Optional<CompletableFuture<Boolean>> verificationFuture = block.getVerificationFuture();
                    if (verificationFuture.isPresent()) {
                        verificationFuture.get().whenComplete((verified, error1) -> {
                            if (error1 != null) {

                            } else if (verified) {
                                completedPieces.add(piece.getPieceIndex());
                                eventSink.firePieceVerified(context.getTorrentId(), piece.getPieceIndex());
                            } else {

                            }
                        });
                    }
                }
            });
        }
    }

    private void disposeOfBlock(Piece piece) {
        BufferedData buffer = bufferedPieceRegistry.getBufferedPiece(piece.getPieceIndex(), piece.getOffset());
        if (buffer != null) {
            buffer.dispose();
        }
    }

    private boolean checkBlockIsExpected(ConnectionState connectionState, Piece piece) {
        Object key = Mapper.mapper().buildKey(piece.getPieceIndex(), piece.getOffset(), piece.getLength());
        return connectionState.getPendingRequests().remove(key);
    }

    private /*nullable*/CompletableFuture<BlockWrite> addBlock(Peer peer, ConnectionState connectionState, Piece piece) {
        int pieceIndex = piece.getPieceIndex(),
                offset = piece.getOffset(),
                blockLength = piece.getLength();

        connectionState.incrementDownloaded(piece.getLength());
        if (connectionState.getCurrentAssignment().isPresent()) {
            Assignment assignment = connectionState.getCurrentAssignment().get();
            if (assignment.isAssigned(pieceIndex)) {
                assignment.check();
            }
        }

        BufferedData buffer = bufferedPieceRegistry.getBufferedPiece(pieceIndex, offset);
        if (buffer == null) {

            return null;
        }
        CompletableFuture<BlockWrite> future = dataWorker.addBlock(torrentId, peer, pieceIndex, offset, buffer);
        connectionState.getPendingWrites().put(
                Mapper.mapper().buildKey(pieceIndex, offset, blockLength), future);
        return future;
    }

    public void produce(Consumer<Message> messageConsumer, MessageContext context) {
        Integer completedPiece;
        while ((completedPiece = completedPieces.poll()) != null) {
            messageConsumer.accept(new Have(completedPiece));
        }
    }
}
