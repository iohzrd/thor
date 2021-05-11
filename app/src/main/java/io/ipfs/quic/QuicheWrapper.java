package io.ipfs.quic;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.incubator.codec.quic.DirectIoByteBufAllocator;
import io.netty.incubator.codec.quic.Quiche;
import io.netty.util.ReferenceCountUtil;

public class QuicheWrapper {


    private static ByteBuf direct(@NonNull ByteBuf msg) {
        ByteBuf buffer = (ByteBuf) msg;
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

    public static void writeAndFlush(long connection, long streamId, ByteBuf message) {
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
}


