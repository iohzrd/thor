package threads.thor.bt.torrent;


import java.util.ArrayList;
import java.util.List;

import threads.thor.bt.IConsumers;
import threads.thor.bt.event.EventSink;
import threads.thor.bt.net.ConnectionKey;
import threads.thor.bt.protocol.BitOrder;
import threads.thor.bt.protocol.Bitfield;
import threads.thor.bt.protocol.Have;
import threads.thor.bt.protocol.Message;


public class BitfieldConsumer implements IConsumers {

    private final threads.thor.bt.data.Bitfield bitfield;
    private final PieceStatistics pieceStatistics;
    private final EventSink eventSink;

    public BitfieldConsumer(threads.thor.bt.data.Bitfield bitfield, PieceStatistics pieceStatistics, EventSink eventSink) {
        this.bitfield = bitfield;
        this.pieceStatistics = pieceStatistics;
        this.eventSink = eventSink;
    }

    @Override
    public void doConsume(Message message, MessageContext messageContext) {
        if (message instanceof Bitfield) {
            consume((Bitfield) message, messageContext);
        }
        if (message instanceof Have) {
            consume((Have) message, messageContext);
        }
    }

    @Override
    public List<MessageConsumer<? extends Message>> getConsumers() {
        List<MessageConsumer<? extends Message>> list = new ArrayList<>();
        list.add(new MessageConsumer<Bitfield>() {
            @Override
            public Class<Bitfield> getConsumedType() {
                return Bitfield.class;
            }

            @Override
            public void consume(Bitfield message, MessageContext context) {
                doConsume(message, context);
            }
        });
        list.add(new MessageConsumer<Have>() {
            @Override
            public Class<Have> getConsumedType() {
                return Have.class;
            }

            @Override
            public void consume(Have message, MessageContext context) {
                doConsume(message, context);
            }
        });
        return list;
    }


    private void consume(Bitfield bitfieldMessage, MessageContext context) {
        ConnectionKey peer = context.getConnectionKey();
        threads.thor.bt.data.Bitfield peerBitfield = new threads.thor.bt.data.Bitfield(
                bitfieldMessage.getBitfield(), BitOrder.LITTLE_ENDIAN, bitfield.getPiecesTotal());
        pieceStatistics.addBitfield(peer, peerBitfield);
        eventSink.firePeerBitfieldUpdated(context.getTorrentId(), peer, peerBitfield);
    }

    private void consume(Have have, MessageContext context) {
        ConnectionKey peer = context.getConnectionKey();
        pieceStatistics.addPiece(peer, have.getPieceIndex());
        pieceStatistics.getPeerBitfield(peer).ifPresent(
                bitfield -> eventSink.firePeerBitfieldUpdated(context.getTorrentId(), peer, bitfield));
    }
}
