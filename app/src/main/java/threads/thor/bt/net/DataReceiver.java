package threads.thor.bt.net;

import java.nio.channels.SelectableChannel;

import threads.thor.bt.net.pipeline.ChannelHandlerContext;

public interface DataReceiver {

    /**
     * Register a channel.
     *
     * @param channel Channel to be registered
     * @param context Context with callbacks for registration/selection/interest change events.
     * @since 1.6
     */
    void registerChannel(SelectableChannel channel, ChannelHandlerContext context);

    /**
     * @since 1.6
     */
    void unregisterChannel(SelectableChannel channel);

    /**
     * Activate selection for the provided channel.
     *
     * @since 1.6
     */
    void activateChannel(SelectableChannel channel);

    /**
     * De-activate selection for the provided channel.
     *
     * @since 1.6
     */
    void deactivateChannel(SelectableChannel channel);
}
