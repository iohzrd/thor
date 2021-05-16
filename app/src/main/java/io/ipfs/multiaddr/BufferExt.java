package io.ipfs.multiaddr;


import androidx.annotation.NonNull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;


public class BufferExt {
    @NonNull
    public static byte[] toByteArray(@NonNull ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.slice().readBytes(bytes);
        return bytes;
    }

    @NonNull
    public static byte[] toByteArray(@NonNull ByteBuf byteBuf, int from, int to) {

        int len = byteBuf.readableBytes();
        int toFinal = Math.min(len, to);
        len = toFinal - from;
        byte[] ret = new byte[len];
        byteBuf.slice(from, toFinal - from).readBytes(ret);
        return ret;
    }

    @NonNull
    public static ByteBuf toByteBuf(@NonNull byte[] bytes) {
        return Unpooled.wrappedBuffer(bytes);
    }
}
