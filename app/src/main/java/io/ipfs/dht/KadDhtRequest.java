package io.ipfs.dht;

import androidx.annotation.NonNull;

import com.google.protobuf.MessageLite;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import dht.pb.Dht;
import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.core.ProtocolIssue;
import io.ipfs.host.LiteHost;
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
    private long start = System.currentTimeMillis();
    private DataHandler reader = new DataHandler(IPFS.PROTOCOL_READER_LIMIT);

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
        LogUtils.debug(TAG, ctx.channel().parent().attr(LiteHost.PEER_KEY).get() + " " + cause);
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
                if (Objects.equals(token, IPFS.DHT_PROTOCOL)) {
                    start = System.currentTimeMillis();
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
                LogUtils.debug(TAG, "done " + (System.currentTimeMillis() - start) + " "
                        + ctx.channel().parent().attr(LiteHost.PEER_KEY).get());
                return;
            }
            reader = new DataHandler(IPFS.MESSAGE_SIZE_MAX);
        } else {
            LogUtils.debug(TAG, "iteration " + reader.hasRead() + " "
                    + reader.expectedBytes() + " " + ctx.name() + " "
                    + ctx.channel().parent().attr(LiteHost.PEER_KEY).get());
        }
    }
}
