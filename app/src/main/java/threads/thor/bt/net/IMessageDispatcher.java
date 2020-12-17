package threads.thor.bt.net;

import java.util.function.Consumer;
import java.util.function.Supplier;

import threads.thor.bt.protocol.Message;

public interface IMessageDispatcher {

    /**
     * Add a message consumer to receive messages from a remote peer for a given threads.torrent.
     *
     * @since 1.7
     */
    void addMessageConsumer(ConnectionKey connectionKey, Consumer<Message> messageConsumer);

    /**
     * Add a message supplier to send messages to a remote peer for a given threads.torrent.
     *
     * @since 1.7
     */
    void addMessageSupplier(ConnectionKey connectionKey, Supplier<Message> messageSupplier);
}
