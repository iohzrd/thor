package threads.thor.magnet.torrent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import threads.thor.magnet.IAgent;
import threads.thor.magnet.IConsumers;
import threads.thor.magnet.net.ConnectionKey;
import threads.thor.magnet.protocol.Bitfield;
import threads.thor.magnet.protocol.Have;
import threads.thor.magnet.protocol.Message;


public class BitfieldCollectingConsumer implements IAgent, IConsumers {

    private final ConcurrentMap<ConnectionKey, byte[]> bitfields;
    private final ConcurrentMap<ConnectionKey, Set<Integer>> haves;

    public BitfieldCollectingConsumer() {
        this.bitfields = new ConcurrentHashMap<>();
        this.haves = new ConcurrentHashMap<>();
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
        bitfields.put(context.getConnectionKey(), bitfieldMessage.getBitfield());
    }

    private void consume(Have have, MessageContext context) {
        ConnectionKey peer = context.getConnectionKey();
        Set<Integer> peerHaves = haves.computeIfAbsent(peer, k -> ConcurrentHashMap.newKeySet());
        peerHaves.add(have.getPieceIndex());
    }

    public Map<ConnectionKey, byte[]> getBitfields() {
        return bitfields;
    }

    public Map<ConnectionKey, Set<Integer>> getHaves() {
        return haves;
    }
}
