package threads.thor.magnet;


import java.util.List;

import threads.thor.magnet.protocol.Message;
import threads.thor.magnet.torrent.MessageConsumer;
import threads.thor.magnet.torrent.MessageContext;

public interface IConsumers extends IAgent {
    void doConsume(Message message, MessageContext messageContext);

    List<MessageConsumer<? extends Message>> getConsumers();
}
