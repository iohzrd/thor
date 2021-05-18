package io.ipfs.dht;

import androidx.annotation.NonNull;

import com.google.protobuf.MessageLite;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import dht.pb.Dht;
import io.LogUtils;
import io.core.ProtocolIssue;
import io.ipfs.IPFS;
import io.ipfs.utils.DataHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.incubator.codec.quic.QuicStreamChannel;

public class KadDhtRequest extends SimpleChannelInboundHandler<ByteBuf> {
    private static final String TAG = KadDhtRequest.class.getSimpleName();
    @NonNull
    private final CompletableFuture<Dht.Message> request;
    @NonNull
    private final CompletableFuture<Void> activation;
    @NonNull
    private final MessageLite messageLite;
    private DataHandler reader = new DataHandler(25000);

    public KadDhtRequest(@NonNull CompletableFuture<Void> activation,
                         @NonNull CompletableFuture<Dht.Message> request,
                         @NonNull MessageLite messageLite) {
        this.request = request;
        this.activation = activation;
        this.messageLite = messageLite;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        LogUtils.error(TAG, " " + cause);
        request.completeExceptionally(cause);
        activation.completeExceptionally(cause);
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

            for (String token : reader.getTokens()) {
                LogUtils.verbose(TAG, "request " + token);
                if (Objects.equals(token, IPFS.KAD_DHT_PROTOCOL)) {
                    activation.complete(
                            ctx.writeAndFlush(DataHandler.encode(messageLite))
                                    .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).get());
                } else if (!Objects.equals(token, IPFS.STREAM_PROTOCOL)) {
                    throw new ProtocolIssue();
                }
            }

            byte[] message = reader.getMessage();

            if (message != null) {
                request.complete(Dht.Message.parseFrom(message));
                ctx.close().get();
            }
            reader = new DataHandler(25000);
        } else {
            LogUtils.debug(TAG, "iteration " + reader.hasRead() + " "
                    + reader.expectedBytes() + " " + ctx.name() + " "
                    + ctx.channel().remoteAddress());
        }
    }
}
