package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.core.ProtocolIssue;
import io.ipfs.utils.DataHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;

public class BitSwapSend extends SimpleChannelInboundHandler<ByteBuf> {

    private static final String TAG = BitSwapSend.class.getSimpleName();

    @NonNull
    private final DataHandler reader = new DataHandler(IPFS.PROTOCOL_READER_LIMIT);

    @NonNull
    private final BitSwap bitSwap;
    @NonNull
    private final CompletableFuture<QuicStreamChannel> stream;

    public BitSwapSend(@NonNull CompletableFuture<QuicStreamChannel> stream,
                       @NonNull BitSwap bitSwap) {
        this.stream = stream;
        this.bitSwap = bitSwap;
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        LogUtils.debug(TAG, "channelUnregistered ");

        QuicChannel quicChannel = (QuicChannel) ctx.channel().parent();

        bitSwap.removeStream(quicChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        LogUtils.error(TAG, " " + cause);
        stream.completeExceptionally(cause);
        ctx.close().get();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg)
            throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        msg.readBytes(out, msg.readableBytes());
        byte[] data = out.toByteArray();
        reader.load(data);

        if (reader.isDone()) {
            for (String received : reader.getTokens()) {
                if (Objects.equals(received, IPFS.BITSWAP_PROTOCOL)) {
                    stream.complete((QuicStreamChannel) ctx.channel());
                } else if (!Objects.equals(received, IPFS.STREAM_PROTOCOL)) {
                    LogUtils.error(TAG, received);
                    throw new ProtocolIssue();
                }
            }
        } else {
            LogUtils.error(TAG, "iteration " + data.length + " "
                    + reader.expectedBytes() + " " + ctx.name() + " "
                    + ctx.channel().remoteAddress());
        }
    }
}
