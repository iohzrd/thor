package threads.thor.bt;

import java.util.function.Consumer;

import threads.thor.bt.protocol.Message;
import threads.thor.bt.torrent.MessageContext;

public interface IProduces extends IAgent {
    void produce(Consumer<Message> messageConsumer, MessageContext context);
}
