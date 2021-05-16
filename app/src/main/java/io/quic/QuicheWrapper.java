package io.quic;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

import io.LogUtils;
import io.core.Closeable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.incubator.codec.quic.DirectIoByteBufAllocator;
import io.netty.incubator.codec.quic.QuicStreamIdGenerator;
import io.netty.incubator.codec.quic.QuicStreamPriority;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.incubator.codec.quic.Quiche;
import io.netty.util.ReferenceCountUtil;


public class QuicheWrapper {
    private static final String TAG = QuicheWrapper.class.getSimpleName();

    public static StreamRecvResult streamRecv(long connection, long streamId, ByteBuf buffer) throws Exception {
        ByteBuf finBuffer = new DirectIoByteBufAllocator(ByteBufAllocator.DEFAULT).directBuffer(1);
        try {
            int writerIndex = buffer.writerIndex();
            long memoryAddress = Quiche.memoryAddress(buffer);
            int recvLen = Quiche.quiche_conn_stream_recv(connection, streamId,
                    memoryAddress + writerIndex, buffer.writableBytes(), Quiche.memoryAddress(finBuffer));
            if (Quiche.throwIfError(recvLen)) {
                return StreamRecvResult.DONE;
            } else {
                buffer.writerIndex(writerIndex + recvLen);
            }
            return finBuffer.getBoolean(0) ? StreamRecvResult.FIN : StreamRecvResult.OK;
        } finally {
            finBuffer.release();
        }
    }

    public static QuicheStream createStream(@NonNull Closeable closeable,
                                            QuicStreamType type, @NonNull QuicheStreamStreamHandler streamHandler,
                                            QuicStreamIdGenerator idGenerator, long connection) throws Exception {

        long streamId = idGenerator.nextStreamId(type == QuicStreamType.BIDIRECTIONAL);

        Quiche.throwIfError(streamSend0(connection, streamId, Unpooled.EMPTY_BUFFER, false));

        QuicheStream quicheStream = new QuicheStream(connection, streamId);
        new Thread(() -> {
            try {
                quicheStream.readStream(closeable, streamHandler);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }).start();
        return quicheStream;
    }

    public static void streamPriority(long connection, long streamId, QuicStreamPriority priority) {
        try {
            streamPriority(connection, streamId, (byte) priority.urgency(), priority.isIncremental());
        } catch (Throwable cause) {
            LogUtils.error(QuicheWrapper.class.getSimpleName(), cause);
        }
    }

    private static void streamPriority(long connection, long streamId, byte priority, boolean incremental)
            throws Exception {
        Quiche.throwIfError(Quiche.quiche_conn_stream_priority(connection, streamId,
                priority, incremental));
    }

    private static ByteBuf direct(@NonNull ByteBuf msg) {
        ByteBuf buffer = msg;
        if (!buffer.isDirect()) {
            DirectIoByteBufAllocator allocator = new DirectIoByteBufAllocator(ByteBufAllocator.DEFAULT);
            ByteBuf tmpBuffer = allocator.directBuffer(buffer.readableBytes());
            tmpBuffer.writeBytes(buffer, buffer.readerIndex(), buffer.readableBytes());
            buffer.release();
            msg = tmpBuffer;
            return msg;
        }
        return msg;
    }

    public static void writeAndFin(long connection, long streamId, ByteBuf message) {
        ByteBuf msg = direct(message);
        try {
            streamSend(connection, streamId, msg, true);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    public static void write(long connection, long streamId, ByteBuf message) {
        ByteBuf msg = direct(message);
        try {
            streamSend(connection, streamId, msg, false);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    public static int streamSend(long connection, long streamId, @NonNull ByteBuf buffer, boolean fin) {
        if (buffer.nioBufferCount() == 1) {
            return streamSend0(connection, streamId, buffer, fin);
        }
        ByteBuffer[] nioBuffers = buffer.nioBuffers();
        int lastIdx = nioBuffers.length - 1;
        int res = 0;
        for (int i = 0; i < lastIdx; i++) {
            ByteBuffer nioBuffer = nioBuffers[i];
            while (nioBuffer.hasRemaining()) {
                int localRes = streamSend(connection, streamId, nioBuffer, false);
                if (localRes <= 0) {
                    return res;
                }
                res += localRes;

                nioBuffer.position(nioBuffer.position() + localRes);
            }
        }
        int localRes = streamSend(connection, streamId, nioBuffers[lastIdx], fin);
        if (localRes > 0) {
            res += localRes;
        }
        return res;
    }

    public static void streamShutdown(long connection, long streamId, boolean read, boolean write, int err) {

        Quiche.quiche_conn_stream_finished(connection, streamId);
        int res = 0;
        if (read) {
            res |= Quiche.quiche_conn_stream_shutdown(connection, streamId, Quiche.QUICHE_SHUTDOWN_READ, err);
        }
        if (write) {
            res |= Quiche.quiche_conn_stream_shutdown(connection, streamId, Quiche.QUICHE_SHUTDOWN_WRITE, err);
        }

    }

    private static int streamSend0(long connection, long streamId, @NonNull ByteBuf buffer, boolean fin) {
        return Quiche.quiche_conn_stream_send(connection, streamId,
                Quiche.memoryAddress(buffer) + buffer.readerIndex(), buffer.readableBytes(), fin);
    }

    private static int streamSend(long connection, long streamId, @NonNull ByteBuffer buffer, boolean fin) {
        return Quiche.quiche_conn_stream_send(connection, streamId,
                Quiche.memoryAddress(buffer) + buffer.position(), buffer.remaining(), fin);
    }

    public static void streamSendFin(long connection, long streamId) throws Exception {
        // Just write an empty buffer and set fin to true.
        Quiche.throwIfError(streamSend0(connection, streamId, Unpooled.EMPTY_BUFFER, true));

    }

    enum StreamRecvResult {
        /**
         * Nothing more to read from the stream.
         */
        DONE,
        /**
         * FIN flag received.
         */
        FIN,
        /**
         * Normal read without FIN flag.
         */
        OK
    }

    public abstract static class QuicheStreamStreamHandler {

        public void channelRead(QuicheStream ctx, ByteBuf byteBuf, boolean fin) {

            try {
                if (byteBuf.capacity() > 0) {
                    channelRead0(ctx, byteBuf);
                }

                if (fin) {
                    QuicheWrapper.streamShutdown(ctx.connection,
                            ctx.streamId, true, true, 0);
                    ctx.close();
                }
            } catch (Throwable throwable) {
                exceptionCaught(ctx, throwable);
            } finally {
                ReferenceCountUtil.release(byteBuf);
            }
        }

        abstract public void exceptionCaught(QuicheStream ctx, Throwable cause);

        abstract public void channelRead0(QuicheStream ctx, ByteBuf byteBuf) throws Exception;
    }

    public static class QuicheStream {
        private final long connection;
        private final long streamId;

        private boolean closed = false;

        public QuicheStream(long connection, long streamId) {
            this.connection = connection;
            this.streamId = streamId;
        }

        public long connection() {
            return connection;
        }

        public long streamId() {
            return streamId;
        }

        public void updatePriority(QuicStreamPriority priority) {
            QuicheWrapper.streamPriority(connection, streamId, priority);
        }

        public void close() {
            closed = true;

            QuicheWrapper.streamShutdown(connection(), streamId(),
                    true, true, 0);
        }

        private boolean isClosed() {
            return closed;
        }

        void readStream(@NonNull Closeable closeable, @NonNull QuicheStreamStreamHandler streamHandler) {
            final long[] readableStreams = new long[128];

            while (!isClosed() && !closeable.isClosed()) {

                long readableIterator = Quiche.quiche_conn_readable(connection);
                if (readableIterator != -1) {
                    int read = Quiche.quiche_stream_iter_next(
                            readableIterator, readableStreams);
                    for (int i = 0; i < read; i++) {

                        if (streamId == readableStreams[i]) {


                            boolean finReceived = false;
                            boolean readable = true;
                            //ChannelPipeline pipeline = pipeline();
                            //  QuicheQuicStreamChannelConfig config = (QuicheQuicStreamChannelConfig) config();
                            // Directly access the DirectIoByteBufAllocator as we need an direct buffer to read into in all cases
                            // even if there is no Unsafe present and the direct buffer is not pooled.
                            DirectIoByteBufAllocator allocator = new DirectIoByteBufAllocator(ByteBufAllocator.DEFAULT);
                            @SuppressWarnings("deprecation")
                            RecvByteBufAllocator.Handle allocHandle = new AdaptiveRecvByteBufAllocator().newHandle();


                            // We should loop as long as a read() was requested and there is anything left to read, which means the
                            // stream was marked as readable before.

                            ByteBuf byteBuf;

                            // It's possible that the stream was marked as finish while we iterated over the readable streams
                            // or while we did have auto read disabled. If so we need to ensure we not try to read from it as it
                            // would produce an error.
                            boolean readCompleteNeeded = false;
                            boolean continueReading = true;
                            try {
                                while (!finReceived && continueReading && readable) {
                                    byteBuf = allocHandle.allocate(allocator);
                                    switch (streamRecv(connection, streamId, byteBuf)) {
                                        case DONE:
                                            // Nothing left to read;
                                            readable = false;
                                            break;
                                        case FIN:
                                            // If we received a FIN we also should mark the channel as non readable as
                                            // there is nothing left to read really.
                                            readable = false;
                                            finReceived = true;
                                            // inputShutdown = true;
                                            break;
                                        case OK:
                                            break;
                                        default:
                                            throw new Error();
                                    }
                                    allocHandle.lastBytesRead(byteBuf.readableBytes());
                                    if (allocHandle.lastBytesRead() <= 0) {
                                        byteBuf.release();
                                        if (finReceived) {
                                            // If we read QuicStreamFrames we should fire an frame through the pipeline
                                            // with an empty buffer but the fin flag set to true.
                                            byteBuf = Unpooled.EMPTY_BUFFER;
                                        } else {
                                            byteBuf = null;
                                            break;
                                        }
                                    }
                                    // We did read one message.
                                    allocHandle.incMessagesRead(1);
                                    readCompleteNeeded = true;


                                    streamHandler.channelRead(this, byteBuf, finReceived);

                                    byteBuf = null;
                                    continueReading = allocHandle.continueReading();
                                }

                                if (readCompleteNeeded) {
                                    allocHandle.readComplete();
                                }

                            } catch (Throwable cause) {
                                readable = false;
                                //  handleReadException(pipeline, byteBuf, cause, allocHandle, readFrames);
                            }
                        }
                    }
                }
            }
        }

        public void write(ByteBuf message) {
            QuicheWrapper.write(connection, streamId, message);
        }

        public void writeAndFin(ByteBuf message) {
            QuicheWrapper.writeAndFin(connection, streamId, message);
        }
    }
}


