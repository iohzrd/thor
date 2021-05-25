package io.ipfs.relay;

import androidx.annotation.NonNull;

import com.google.protobuf.MessageLite;

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
import relay.pb.Relay;

public class RelayRequest extends SimpleChannelInboundHandler<ByteBuf> {
    private static final String TAG = RelayRequest.class.getSimpleName();
    @NonNull
    private final CompletableFuture<Relay.CircuitRelay> request;
    @NonNull
    private final CompletableFuture<Void> activation;
    @NonNull
    private final MessageLite messageLite;
    private DataHandler reader = new DataHandler(4096);

    public RelayRequest(@NonNull CompletableFuture<Void> activation,
                        @NonNull CompletableFuture<relay.pb.Relay.CircuitRelay> request,
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
                LogUtils.error(TAG, "request " + token);
                if (Objects.equals(token, IPFS.RELAY_PROTOCOL)) {
                    activation.complete(ctx.writeAndFlush(DataHandler.encode(messageLite)).get());
                } else if (!Objects.equals(token, IPFS.STREAM_PROTOCOL)) {
                    throw new ProtocolIssue();
                }
            }

            byte[] message = reader.getMessage();

            if (message != null) {
                request.complete(Relay.CircuitRelay.parseFrom(message));
            }
            reader = new DataHandler(4096);
        } else {
            LogUtils.debug(TAG, "iteration " + reader.hasRead() + " "
                    + reader.expectedBytes() + " " + ctx.name() + " "
                    + ctx.channel().remoteAddress());
        }
    }
}
