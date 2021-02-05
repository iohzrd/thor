package threads.thor.magnet.net.pipeline;

public interface ChannelHandlerContext {

    /**
     * @since 1.6
     */
    ChannelPipeline pipeline();

    /**
     * Request reading from the channel
     *
     * @return true, if all data has been read
     * @since 1.9
     */
    boolean readFromChannel();

    // TODO: I guess this can be removed
    // we can instead use a series of ChannelPipeline.decode() invocations for the same effect
    void fireDataReceived();
}
