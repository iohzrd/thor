
package threads.thor.bt.torrent;

import threads.thor.bt.protocol.Message;

public interface MessageConsumer<T extends Message> {

    /**
     * @return Message type, that this consumer is interested in
     * @since 1.0
     */
    Class<T> getConsumedType();

    /**
     * @since 1.0
     */
    void consume(T message, MessageContext context);
}
