package threads.thor.magnet;

import java.util.function.Consumer;

import threads.thor.magnet.protocol.Message;
import threads.thor.magnet.torrent.MessageContext;

public interface IProduces extends IAgent {
    void produce(Consumer<Message> messageConsumer, MessageContext context);
}
