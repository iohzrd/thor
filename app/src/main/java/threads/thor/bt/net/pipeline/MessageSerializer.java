package threads.thor.bt.net.pipeline;

import java.nio.ByteBuffer;

import threads.thor.bt.net.Peer;
import threads.thor.bt.protocol.EncodingContext;
import threads.thor.bt.protocol.Message;
import threads.thor.bt.protocol.handler.MessageHandler;

class MessageSerializer {

    private final EncodingContext context;
    private final MessageHandler<Message> protocol;

    public MessageSerializer(Peer peer,
                             MessageHandler<Message> protocol) {
        this.context = new EncodingContext(peer);
        this.protocol = protocol;
    }

    public boolean serialize(Message message, ByteBuffer buffer) {
        return protocol.encode(context, message, buffer);
    }
}
