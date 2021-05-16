package io.core;

import org.jetbrains.annotations.NotNull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;


public class BufferExt {
    @NotNull
    public static byte[] toByteArray(@NotNull ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.slice().readBytes(bytes);
        return bytes;
    }

    @NotNull
    public static byte[] toByteArray(@NotNull ByteBuf byteBuf, int from, int to) {

        int len = byteBuf.readableBytes();
        boolean var5 = false;
        int toFinal = Math.min(len, to);
        len = toFinal - from;
        byte[] ret = new byte[len];
        byteBuf.slice(from, toFinal - from).readBytes(ret);
        return ret;
    }

    @NotNull
    public static ByteBuf toByteBuf(@NotNull byte[] bytes) {
        return Unpooled.wrappedBuffer(bytes);
    }
}
