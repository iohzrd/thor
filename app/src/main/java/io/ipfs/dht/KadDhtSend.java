package io.ipfs.dht;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import dht.pb.Dht;
import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.core.ProtocolIssue;
import io.ipfs.utils.DataHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class KadDhtSend extends SimpleChannelInboundHandler<ByteBuf> {

    private static final String TAG = KadDhtSend.class.getSimpleName();

    @NonNull
    private final DataHandler reader = new DataHandler(1000);

    @NonNull
    private final CompletableFuture<Void> stream;
    @NonNull
    private final Dht.Message message;

    public KadDhtSend(@NonNull CompletableFuture<Void> stream, @NonNull Dht.Message message) {
        this.stream = stream;
        this.message = message;
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
                if (Objects.equals(received, IPFS.DHT_PROTOCOL)) {
                    stream.complete(ctx.writeAndFlush(DataHandler.encode(message.toByteArray())).get());
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
