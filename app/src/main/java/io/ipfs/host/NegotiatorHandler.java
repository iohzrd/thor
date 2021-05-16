package io.ipfs.host;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import io.LogUtils;
import io.core.ProtocolIssue;
import io.ipfs.IPFS;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;

public class NegotiatorHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final String TAG = NegotiatorHandler.class.getSimpleName();

    @NonNull
    private final DataHandler reader = new DataHandler(1000);
    @NonNull
    private final String protocol;
    @NonNull
    private final LiteHost host;
    @NonNull
    private final CompletableFuture<QuicStreamChannel> stream;

    public NegotiatorHandler(@NonNull CompletableFuture<QuicStreamChannel> stream,
                             @NonNull LiteHost host,
                             @NonNull String protocol) {
        this.protocol = protocol;
        this.stream = stream;
        this.host = host;
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        LogUtils.error(TAG, "channelUnregistered " + protocol);

        QuicChannel quicChannel = (QuicChannel) ctx.channel().parent();

        host.removeStream(quicChannel, protocol);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        LogUtils.error(TAG, protocol + " " + cause);
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
                if (Objects.equals(received, protocol)) {
                    stream.complete((QuicStreamChannel)ctx.channel());
                } else if(!Objects.equals(received, IPFS.STREAM_PROTOCOL)) {
                    throw new ProtocolIssue();
                }
            }
        } else {
            LogUtils.debug(TAG, "iteration " + data.length + " "
                    + reader.expectedBytes() + " " + protocol + " " + ctx.name() + " "
                    + ctx.channel().remoteAddress());
        }
    }
}
