package threads.thor.bt.net.pipeline;

import threads.thor.bt.protocol.Message;

public interface ChannelHandler {

    /**
     * @since 1.6
     */
    void send(Message message);

    /**
     * @return Message or null, if there are no incoming messages
     * @since 1.6
     */
    Message receive();

    /**
     * Request to read incoming data from the underlying channel.
     *
     * @return true, if all data has been read
     * @since 1.9
     */
    boolean read();

    /**
     * @since 1.6
     */
    void register();

    /**
     * @since 1.6
     */
    void unregister();

    /**
     * @since 1.6
     */
    void activate();

    /**
     * @since 1.6
     */
    void deactivate();

    /**
     * Request to write pending outgoing data to the underlying channel.
     *
     * @since 1.6
     */
    void flush();

    /**
     * Request to close.
     * The procedure may involve unregistering, closing the underlying channel and releasing the resources.
     *
     * @since 1.6
     */
    void close();

    /**
     * @return true, if the handler has been closed
     * @since 1.6
     */
    boolean isClosed();
}
