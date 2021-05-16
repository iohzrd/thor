package io.core;

import org.jetbrains.annotations.NotNull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;


public class BufferExt {
    @NotNull
    public static byte[] toByteArray(@NotNull ByteBuf byteBuf) {

        byte[] var1 = new byte[byteBuf.readableBytes()];
        boolean var2 = false;
        boolean var3 = false;
        boolean var5 = false;
        byteBuf.slice().readBytes(var1);
        return var1;
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

    // $FF: synthetic method
    public static byte[] toByteArray$default(ByteBuf var0, int var1, int var2, int var3, Object var4) {
        if ((var3 & 1) != 0) {
            var1 = 0;
        }

        if ((var3 & 2) != 0) {
            var2 = Integer.MAX_VALUE;
        }

        return toByteArray(var0, var1, var2);
    }

    @NotNull
    public static ByteBuf toByteBuf(@NotNull byte[] bytes) {
        return Unpooled.wrappedBuffer(bytes);
    }
}
