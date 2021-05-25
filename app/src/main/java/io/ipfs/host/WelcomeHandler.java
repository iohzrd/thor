package io.ipfs.host;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;

import identify.pb.IdentifyOuterClass;
import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.utils.DataHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.incubator.codec.quic.QuicChannel;

public class WelcomeHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private static final String TAG = WelcomeHandler.class.getSimpleName();
    private final LiteHost host;
    private final DataHandler reader = new DataHandler(IPFS.PROTOCOL_READER_LIMIT);


    public WelcomeHandler(@NonNull LiteHost host) {
        this.host = host;
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LogUtils.error(TAG, cause.getClass().getSimpleName());
        ctx.close().get();
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {

        QuicChannel quicChannel = (QuicChannel) ctx.channel().parent();
        LogUtils.error(TAG, "PeerId " + quicChannel.attr(LiteHost.PEER_KEY).get());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        msg.readBytes(out, msg.readableBytes());
        byte[] data = out.toByteArray();
        reader.load(data);

        if (reader.isDone()) {
            for (String token : reader.getTokens()) {
                if (token.equals(IPFS.STREAM_PROTOCOL)) {
                    ctx.write(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
                } else if (token.equals(IPFS.IDENTITY_PROTOCOL)) {
                    ctx.write(DataHandler.writeToken(IPFS.IDENTITY_PROTOCOL));

                    IdentifyOuterClass.Identify response =
                            host.createIdentity(quicChannel.remoteAddress());
                    ctx.writeAndFlush(DataHandler.encode(response)).addListener(
                            future -> ctx.close().get()
                    );

                } else {
                    LogUtils.debug(TAG, token);
                    ctx.writeAndFlush(DataHandler.writeToken(IPFS.NA)).addListener(
                            future -> ctx.close().get()
                    );
                }
            }
        } else {
            LogUtils.error(TAG, "iteration listener " + data.length + " "
                    + reader.expectedBytes() + " " + ctx.name() + " "
                    + ctx.channel().remoteAddress());
        }
    }
}
