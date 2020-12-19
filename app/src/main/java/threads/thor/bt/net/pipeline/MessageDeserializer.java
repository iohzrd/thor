package threads.thor.bt.net.pipeline;

import java.util.Objects;

import threads.thor.bt.net.Peer;
import threads.thor.bt.net.buffer.ByteBufferView;
import threads.thor.bt.protocol.DecodingContext;
import threads.thor.bt.protocol.Message;
import threads.thor.bt.protocol.handler.MessageHandler;

class MessageDeserializer {

    private final MessageHandler<Message> protocol;
    private final Peer peer;

    public MessageDeserializer(Peer peer, MessageHandler<Message> protocol) {
        this.peer = peer;
        this.protocol = protocol;
    }

    public Message deserialize(ByteBufferView buffer) {
        int position = buffer.position();
        int limit = buffer.limit();

        Message message = null;
        DecodingContext context = new DecodingContext(peer);
        int consumed = protocol.decode(context, buffer);
        if (consumed > 0) {
            if (consumed > limit - position) {
                throw new RuntimeException("Unexpected amount of bytes consumed: " + consumed);
            }
            buffer.position(position + consumed);
            message = Objects.requireNonNull(context.getMessage());
        } else {
            buffer.limit(limit);
            buffer.position(position);
        }
        return message;
    }
}
