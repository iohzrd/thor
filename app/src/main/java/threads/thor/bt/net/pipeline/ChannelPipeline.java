package threads.thor.bt.net.pipeline;

import threads.thor.bt.protocol.Message;

public interface ChannelPipeline {

    /**
     * @return Incoming message, if there is sufficient data to decode it, or null
     * @since 1.6
     */
    Message decode();

    /**
     * @param message Outgoing message to encode
     * @return true, if there is sufficient space to encode the message
     * @since 1.6
     */
    boolean encode(Message message);

    /**
     * Attach channel handler to this pipeline
     *
     * @since 1.6
     */
    ChannelHandlerContext bindHandler(ChannelHandler handler);
}
