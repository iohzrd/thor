package threads.thor.bt.net.pipeline;

import threads.thor.bt.net.Peer;

public interface IChannelPipelineFactory {

    /**
     * Start building a pipeline for the given peer.
     *
     * @since 1.6
     */
    ChannelPipelineBuilder buildPipeline(Peer peer);
}
