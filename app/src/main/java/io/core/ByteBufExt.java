package io.core;

import org.jetbrains.annotations.NotNull;

import io.netty.buffer.ByteBuf;


public final class ByteBufExt {
    @NotNull
    public static ByteBuf writeUvarint(@NotNull ByteBuf byteBuf, int value) {

        return writeUvarint(byteBuf, (long) value);
    }

    @NotNull
    public static ByteBuf writeUvarint(@NotNull ByteBuf byteBuf, long value) {


        long v;
        for (v = value; v >= (long) 128; v >>= 7) {
            byteBuf.writeByte((int) (v | 128L));
        }

        byteBuf.writeByte((int) v);
        return byteBuf;
    }

    public static long readUvarint(@NotNull ByteBuf byteBuf) {

        long x = 0L;
        int s = 0;
        int originalReaderIndex = byteBuf.readerIndex();
        int i = 0;

        for (byte var6 = 9; i <= var6; ++i) {
            if (!byteBuf.isReadable()) {
                byteBuf.readerIndex(originalReaderIndex);
                return -1L;
            }

            short b = byteBuf.readUnsignedByte();
            if (b < 128) {
                if (i == 9 && b > 1) {
                    throw new IllegalStateException("Overflow reading uvarint");
                }

                return x | (long) b << s;
            }

            x |= ((long) b & 127L) << s;
            s += 7;
        }

        throw new IllegalStateException("uvarint too long");
    }
}
