package threads.thor.bt.net.pipeline;

import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import threads.thor.bt.net.Peer;
import threads.thor.bt.net.buffer.BorrowedBuffer;
import threads.thor.bt.net.buffer.BufferMutator;
import threads.thor.bt.protocol.Message;
import threads.thor.bt.protocol.handler.MessageHandler;

public abstract class ChannelPipelineBuilder {

    private final Peer peer;
    private ByteChannel channel;
    private MessageHandler<Message> protocol;
    private BorrowedBuffer<ByteBuffer> inboundBuffer;
    private BorrowedBuffer<ByteBuffer> outboundBuffer;
    private List<BufferMutator> decoders;
    private List<BufferMutator> encoders;

    ChannelPipelineBuilder(Peer peer) {
        this.peer = Objects.requireNonNull(peer);
    }

    public ChannelPipelineBuilder channel(ByteChannel channel) {
        this.channel = Objects.requireNonNull(channel);
        return this;
    }

    public ChannelPipelineBuilder protocol(MessageHandler<Message> protocol) {
        this.protocol = Objects.requireNonNull(protocol);
        return this;
    }

    public ChannelPipelineBuilder inboundBuffer(BorrowedBuffer<ByteBuffer> inboundBuffer) {
        this.inboundBuffer = Objects.requireNonNull(inboundBuffer);
        return this;
    }

    public ChannelPipelineBuilder outboundBuffer(BorrowedBuffer<ByteBuffer> outboundBuffer) {
        this.outboundBuffer = Objects.requireNonNull(outboundBuffer);
        return this;
    }

    public ChannelPipelineBuilder decoders(BufferMutator firstDecoder, BufferMutator... otherDecoders) {
        Objects.requireNonNull(firstDecoder);
        decoders = asList(firstDecoder, otherDecoders);
        return this;
    }

    public ChannelPipelineBuilder encoders(BufferMutator firstEncoder, BufferMutator... otherEncoders) {
        Objects.requireNonNull(firstEncoder);
        encoders = asList(firstEncoder, otherEncoders);
        return this;
    }

    private List<BufferMutator> asList(BufferMutator firstMutator, BufferMutator... otherMutators) {
        List<BufferMutator> mutators = new ArrayList<>();
        mutators.add(firstMutator);
        if (otherMutators != null) {
            mutators.addAll(Arrays.asList(otherMutators));
        }
        return mutators;
    }

    public ChannelPipeline build() {
        Objects.requireNonNull(channel, "Missing channel");
        Objects.requireNonNull(protocol, "Missing protocol");

        Optional<BorrowedBuffer<ByteBuffer>> _inboundBuffer = Optional.ofNullable(inboundBuffer);
        Optional<BorrowedBuffer<ByteBuffer>> _outboundBuffer = Optional.ofNullable(outboundBuffer);
        List<BufferMutator> _decoders = (decoders == null) ? Collections.emptyList() : decoders;
        List<BufferMutator> _encoders = (encoders == null) ? Collections.emptyList() : encoders;

        return doBuild(peer, channel, protocol, _inboundBuffer, _outboundBuffer, _decoders, _encoders);
    }

    protected abstract ChannelPipeline doBuild(
            Peer peer,
            ByteChannel channel,
            MessageHandler<Message> protocol,
            Optional<BorrowedBuffer<ByteBuffer>> inboundBuffer,
            Optional<BorrowedBuffer<ByteBuffer>> outboundBuffer,
            List<BufferMutator> decoders,
            List<BufferMutator> encoders);
}
