/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package threads.thor.bt.net.pipeline;

import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.List;
import java.util.Optional;

import threads.thor.bt.net.Peer;
import threads.thor.bt.net.buffer.BorrowedBuffer;
import threads.thor.bt.net.buffer.BufferMutator;
import threads.thor.bt.net.buffer.IBufferManager;
import threads.thor.bt.protocol.Message;
import threads.thor.bt.protocol.handler.MessageHandler;

public class ChannelPipelineFactory implements IChannelPipelineFactory {

    private final IBufferManager bufferManager;
    private final IBufferedPieceRegistry bufferedPieceRegistry;


    public ChannelPipelineFactory(IBufferManager bufferManager, IBufferedPieceRegistry bufferedPieceRegistry) {
        this.bufferManager = bufferManager;
        this.bufferedPieceRegistry = bufferedPieceRegistry;
    }

    @Override
    public ChannelPipelineBuilder buildPipeline(Peer peer) {
        return new ChannelPipelineBuilder(peer) {
            @Override
            protected ChannelPipeline doBuild(
                    Peer peer,
                    ByteChannel channel,
                    MessageHandler<Message> protocol,
                    Optional<BorrowedBuffer<ByteBuffer>> inboundBuffer,
                    Optional<BorrowedBuffer<ByteBuffer>> outboundBuffer,
                    List<BufferMutator> decoders,
                    List<BufferMutator> encoders) {

                BorrowedBuffer<ByteBuffer> _inboundBuffer = inboundBuffer.orElseGet(bufferManager::borrowByteBuffer);
                BorrowedBuffer<ByteBuffer> _outboundBuffer = outboundBuffer.orElseGet(bufferManager::borrowByteBuffer);

                return new DefaultChannelPipeline(peer, protocol, _inboundBuffer, _outboundBuffer,
                        decoders, encoders, bufferedPieceRegistry);
            }
        };
    }
}
