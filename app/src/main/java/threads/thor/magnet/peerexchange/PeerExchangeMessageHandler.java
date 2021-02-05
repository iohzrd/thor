package threads.thor.magnet.peerexchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;

import threads.thor.magnet.bencoding.BEMap;
import threads.thor.magnet.bencoding.BEParser;
import threads.thor.magnet.net.buffer.ByteBufferView;
import threads.thor.magnet.protocol.DecodingContext;
import threads.thor.magnet.protocol.EncodingContext;
import threads.thor.magnet.protocol.handler.MessageHandler;

public class PeerExchangeMessageHandler implements MessageHandler<PeerExchange> {

    private final Collection<Class<? extends PeerExchange>> supportedTypes;

    public PeerExchangeMessageHandler() {
        supportedTypes = Collections.singleton(PeerExchange.class);
    }

    @Override
    public Collection<Class<? extends PeerExchange>> getSupportedTypes() {
        return supportedTypes;
    }

    @Override
    public Class<PeerExchange> readMessageType(ByteBufferView buffer) {
        return PeerExchange.class;
    }

    @Override
    public int decode(DecodingContext context, ByteBufferView buffer) {

        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        try (BEParser parser = new BEParser(payload)) {
            BEMap messageContent = parser.readMap();
            PeerExchange message = PeerExchange.parse(messageContent);
            context.setMessage(message);
            return messageContent.getContent().length;
        }
    }

    @Override
    public boolean encode(EncodingContext context, PeerExchange message, ByteBuffer buffer) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            message.writeTo(bos);
        } catch (IOException e) {
            // can't happen
        }

        byte[] payload = bos.toByteArray();
        if (buffer.remaining() < payload.length) {
            return false;
        }

        buffer.put(payload);
        return true;
    }
}
