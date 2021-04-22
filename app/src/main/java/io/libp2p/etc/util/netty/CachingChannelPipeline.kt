package io.libp2p.etc.util.netty

import io.netty.channel.Channel
import io.netty.channel.DefaultChannelPipeline

// TODO experimental
class CachingChannelPipeline(channel: Channel) : DefaultChannelPipeline(channel) {

    enum class EventType { Message, Exception, UserEvent, Active, Inactive }
    class Event(val type: EventType, val data: Any?)

    override fun onUnhandledInboundMessage(msg: Any?) {
        super.onUnhandledInboundMessage(msg)
    }

    override fun onUnhandledInboundUserEventTriggered(evt: Any?) {
        super.onUnhandledInboundUserEventTriggered(evt)
    }

    override fun onUnhandledInboundException(cause: Throwable?) {
        super.onUnhandledInboundException(cause)
    }

}
