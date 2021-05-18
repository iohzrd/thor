package io.ipfs.push;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import io.LogUtils;
import io.core.ProtocolIssue;
import io.ipfs.IPFS;
import io.ipfs.utils.DataHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.incubator.codec.quic.QuicStreamChannel;

public class PushSend extends SimpleChannelInboundHandler<ByteBuf> {

    private static final String TAG = PushSend.class.getSimpleName();

    @NonNull
    private final DataHandler reader = new DataHandler(1000);

    @NonNull
    private final CompletableFuture<QuicStreamChannel> stream;

    public PushSend(@NonNull CompletableFuture<QuicStreamChannel> stream) {
        this.stream = stream;
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
                if (Objects.equals(received, IPFS.PUSH_PROTOCOL)) {
                    stream.complete((QuicStreamChannel) ctx.channel());
                } else if (!Objects.equals(received, IPFS.STREAM_PROTOCOL)) {
                    throw new ProtocolIssue();
                }
            }
        } else {
            LogUtils.debug(TAG, "iteration " + data.length + " "
                    + reader.expectedBytes() + " " + ctx.name() + " "
                    + ctx.channel().remoteAddress());
        }
    }
}
