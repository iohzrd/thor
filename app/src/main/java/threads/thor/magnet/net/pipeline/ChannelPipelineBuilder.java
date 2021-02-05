package threads.thor.magnet.net.pipeline;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import threads.thor.magnet.net.Peer;
import threads.thor.magnet.net.buffer.BorrowedBuffer;
import threads.thor.magnet.net.buffer.BufferMutator;
import threads.thor.magnet.protocol.Message;
import threads.thor.magnet.protocol.handler.MessageHandler;

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

    public void channel(ByteChannel channel) {
        this.channel = Objects.requireNonNull(channel);
    }

    public void protocol(MessageHandler<Message> protocol) {
        this.protocol = Objects.requireNonNull(protocol);
    }

    public void inboundBuffer(BorrowedBuffer<ByteBuffer> inboundBuffer) {
        this.inboundBuffer = Objects.requireNonNull(inboundBuffer);
    }

    public void outboundBuffer(BorrowedBuffer<ByteBuffer> outboundBuffer) {
        this.outboundBuffer = Objects.requireNonNull(outboundBuffer);
    }

    public void decoders(BufferMutator firstDecoder, BufferMutator... otherDecoders) {
        Objects.requireNonNull(firstDecoder);
        decoders = asList(firstDecoder, otherDecoders);
    }

    public void encoders(BufferMutator firstEncoder, BufferMutator... otherEncoders) {
        Objects.requireNonNull(firstEncoder);
        encoders = asList(firstEncoder, otherEncoders);
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


        List<BufferMutator> _decoders = (decoders == null) ? Collections.emptyList() : decoders;
        List<BufferMutator> _encoders = (encoders == null) ? Collections.emptyList() : encoders;

        return doBuild(peer, protocol, inboundBuffer, outboundBuffer, _decoders, _encoders);
    }

    protected abstract ChannelPipeline doBuild(
            Peer peer,
            MessageHandler<Message> protocol,
            @Nullable BorrowedBuffer<ByteBuffer> inboundBuffer,
            @Nullable BorrowedBuffer<ByteBuffer> outboundBuffer,
            List<BufferMutator> decoders,
            List<BufferMutator> encoders);
}
