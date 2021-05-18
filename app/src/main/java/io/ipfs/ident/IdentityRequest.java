package io.ipfs.ident;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import identify.pb.IdentifyOuterClass;
import io.LogUtils;
import io.core.ProtocolIssue;
import io.ipfs.IPFS;
import io.ipfs.utils.DataHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class IdentityRequest extends SimpleChannelInboundHandler<ByteBuf> {
    private static final String TAG = IdentityRequest.class.getSimpleName();
    @NonNull
    private final CompletableFuture<IdentifyOuterClass.Identify> request;
    @NonNull
    private final CompletableFuture<Void> activation;
    private DataHandler reader = new DataHandler(25000);

    public IdentityRequest(@NonNull CompletableFuture<Void> activation,
                           @NonNull CompletableFuture<IdentifyOuterClass.Identify> request) {
        this.request = request;
        this.activation = activation;
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
                if (Objects.equals(token, IPFS.IDENTITY_PROTOCOL)) {
                    activation.complete(null);
                } else if (!Objects.equals(token, IPFS.STREAM_PROTOCOL)) {
                    throw new ProtocolIssue();
                }
            }

            byte[] message = reader.getMessage();

            if (message != null) {
                request.complete(IdentifyOuterClass.Identify.parseFrom(message));
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
