package threads.thor.bt;


import java.util.List;

import threads.thor.bt.protocol.Message;
import threads.thor.bt.torrent.MessageConsumer;
import threads.thor.bt.torrent.MessageContext;

public interface IConsumers extends IAgent {
    void doConsume(Message message, MessageContext messageContext);

    List<MessageConsumer<? extends Message>> getConsumers();
}
