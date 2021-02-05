package threads.thor.magnet.torrent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import threads.thor.magnet.IConsumers;
import threads.thor.magnet.IProduces;
import threads.thor.magnet.data.Bitfield;
import threads.thor.magnet.event.EventSink;
import threads.thor.magnet.metainfo.TorrentId;
import threads.thor.magnet.net.buffer.BufferedData;
import threads.thor.magnet.net.pipeline.BufferedPieceRegistry;
import threads.thor.magnet.protocol.Have;
import threads.thor.magnet.protocol.Message;
import threads.thor.magnet.protocol.Piece;

public class PieceConsumer implements IProduces, IConsumers {


    private final TorrentId torrentId;
    private final Bitfield bitfield;
    private final DataWorker dataWorker;
    private final BufferedPieceRegistry bufferedPieceRegistry;
    private final EventSink eventSink;
    private final Queue<Integer> completedPieces;

    public PieceConsumer(TorrentId torrentId,
                         Bitfield bitfield,
                         DataWorker dataWorker,
                         BufferedPieceRegistry bufferedPieceRegistry,
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

        CompletableFuture<BlockWrite> future = addBlock(connectionState, piece);
        if (future == null) {
            disposeOfBlock(piece);
        } else {
            future.whenComplete((block, error) -> {

                if (!block.isRejected()) {
                    Optional<CompletableFuture<Boolean>> verificationFuture = block.getVerificationFuture();
                    verificationFuture.ifPresent(booleanCompletableFuture -> booleanCompletableFuture.whenComplete((verified, error1) -> {
                        if (error1 == null && verified) {
                            completedPieces.add(piece.getPieceIndex());
                            eventSink.firePieceVerified(context.getTorrentId(), piece.getPieceIndex());
                        }
                    }));
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

    private CompletableFuture<BlockWrite> addBlock(ConnectionState connectionState, Piece piece) {
        int pieceIndex = piece.getPieceIndex(),
                offset = piece.getOffset(),
                blockLength = piece.getLength();

        Assignment assignment = connectionState.getCurrentAssignment();
        if (assignment != null) {
            if (assignment.isAssigned(pieceIndex)) {
                assignment.check();
            }
        }

        BufferedData buffer = bufferedPieceRegistry.getBufferedPiece(pieceIndex, offset);
        if (buffer == null) {

            return null;
        }
        CompletableFuture<BlockWrite> future = dataWorker.addBlock(torrentId, pieceIndex, offset, buffer);
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
