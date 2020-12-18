package threads.thor.bt.net.pipeline;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.util.List;

import threads.thor.bt.net.Peer;
import threads.thor.bt.net.buffer.BorrowedBuffer;
import threads.thor.bt.net.buffer.BufferMutator;
import threads.thor.bt.net.buffer.IBufferManager;
import threads.thor.bt.protocol.Message;
import threads.thor.bt.protocol.handler.MessageHandler;

public class ChannelPipelineFactory {

    private final IBufferManager bufferManager;
    private final BufferedPieceRegistry bufferedPieceRegistry;


    public ChannelPipelineFactory(IBufferManager bufferManager, BufferedPieceRegistry bufferedPieceRegistry) {
        this.bufferManager = bufferManager;
        this.bufferedPieceRegistry = bufferedPieceRegistry;
    }


    public ChannelPipelineBuilder buildPipeline(Peer peer) {
        return new ChannelPipelineBuilder(peer) {
            @Override
            protected ChannelPipeline doBuild(
                    Peer peer,
                    MessageHandler<Message> protocol,
                    @Nullable BorrowedBuffer<ByteBuffer> inboundBuffer,
                    @Nullable BorrowedBuffer<ByteBuffer> outboundBuffer,
                    List<BufferMutator> decoders,
                    List<BufferMutator> encoders) {

                BorrowedBuffer<ByteBuffer> _inboundBuffer = bufferManager.borrowByteBuffer();
                if (inboundBuffer != null) {
                    _inboundBuffer = inboundBuffer;
                }
                BorrowedBuffer<ByteBuffer> _outboundBuffer = bufferManager.borrowByteBuffer();
                if (_outboundBuffer != null) {
                    _outboundBuffer = outboundBuffer;
                }
                return new ChannelPipeline(peer, protocol, _inboundBuffer, _outboundBuffer,
                        decoders, encoders, bufferedPieceRegistry);
            }
        };
    }
}
