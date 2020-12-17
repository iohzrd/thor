package threads.thor.bt.net.pipeline;

import java.nio.ByteBuffer;
import java.util.List;

import threads.thor.bt.net.Peer;
import threads.thor.bt.net.buffer.BorrowedBuffer;
import threads.thor.bt.net.buffer.BufferMutator;
import threads.thor.bt.protocol.Message;
import threads.thor.bt.protocol.handler.MessageHandler;

public class DefaultChannelPipeline implements ChannelPipeline {

    private final InboundMessageProcessor inboundMessageProcessor;
    private final MessageSerializer serializer;

    private final BorrowedBuffer<ByteBuffer> inboundBuffer;
    private final BorrowedBuffer<ByteBuffer> outboundBuffer;
    private final List<BufferMutator> encoders;

    private DefaultChannelHandlerContext context;

    public DefaultChannelPipeline(
            Peer peer,
            MessageHandler<Message> protocol,
            BorrowedBuffer<ByteBuffer> inboundBuffer,
            BorrowedBuffer<ByteBuffer> outboundBuffer,
            List<BufferMutator> decoders,
            List<BufferMutator> encoders,
            IBufferedPieceRegistry bufferedPieceRegistry) {

        ByteBuffer buffer;
        try {
            buffer = inboundBuffer.lockAndGet();
        } finally {
            inboundBuffer.unlock();
        }

        this.inboundMessageProcessor = new InboundMessageProcessor(peer, buffer,
                new MessageDeserializer(peer, protocol), decoders, bufferedPieceRegistry);
        this.serializer = new MessageSerializer(peer, protocol);
        this.inboundBuffer = inboundBuffer;
        this.outboundBuffer = outboundBuffer;
        this.encoders = encoders;

        // process existing data immediately (e.g. there might be leftovers from MSE handshake)
        fireDataReceived();
    }

    @Override
    public Message decode() {
        checkHandlerIsBound();

        return inboundMessageProcessor.pollMessage();
    }

    private void fireDataReceived() {
        try {
            inboundBuffer.lockAndGet();
            inboundMessageProcessor.processInboundData();
        } finally {
            inboundBuffer.unlock();
        }
    }

    @Override
    public boolean encode(Message message) {
        checkHandlerIsBound();

        ByteBuffer buffer = outboundBuffer.lockAndGet();
        if (buffer == null) {
            // buffer has been released
            // TODO: So what? Maybe throw an exception then?
            return false;
        }

        try {
            return writeMessageToBuffer(message, buffer);
        } finally {
            outboundBuffer.unlock();
        }
    }

    private boolean writeMessageToBuffer(Message message, ByteBuffer buffer) {
        int encodedDataLimit = buffer.position();
        boolean written = serializer.serialize(message, buffer);
        if (written) {
            int unencodedDataLimit = buffer.position();
            buffer.flip();
            encoders.forEach(mutator -> {
                buffer.position(encodedDataLimit);
                mutator.mutate(buffer);
            });
            buffer.clear();
            buffer.position(unencodedDataLimit);
        }
        return written;
    }

    private void checkHandlerIsBound() {
        if (context == null) {
            throw new IllegalStateException("Channel handler is not bound");
        }
    }

    @Override
    public ChannelHandlerContext bindHandler(ChannelHandler handler) {
        if (context != null) {
            if (handler == context.handler()) {
                return context;
            } else {
                throw new IllegalStateException("Already bound to different handler");
            }
        }

        context = new DefaultChannelHandlerContext(handler, this);
        return context;
    }

    private static class DefaultChannelHandlerContext implements ChannelHandlerContext {

        private final ChannelHandler handler;
        private final DefaultChannelPipeline pipeline;

        DefaultChannelHandlerContext(ChannelHandler handler, DefaultChannelPipeline pipeline) {
            this.handler = handler;
            this.pipeline = pipeline;
        }

        ChannelHandler handler() {
            return handler;
        }

        @Override
        public ChannelPipeline pipeline() {
            return pipeline;
        }

        @Override
        public boolean readFromChannel() {
            return handler.read();
        }

        @Override
        public void fireChannelRegistered() {
            // TODO
        }

        @Override
        public void fireChannelUnregistered() {
            // TODO
        }

        @Override
        public void fireChannelActive() {
            // TODO
        }

        @Override
        public void fireChannelInactive() {
            // TODO
        }

        @Override
        public void fireDataReceived() {
            pipeline.fireDataReceived();
        }
    }
}
