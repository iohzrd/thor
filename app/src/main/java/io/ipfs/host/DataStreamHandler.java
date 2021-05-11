package io.ipfs.host;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bitswap.pb.MessageOuterClass;
import identify.pb.IdentifyOuterClass;
import io.LogUtils;
import io.ipfs.IPFS;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.incubator.codec.quic.QuicChannel;

public class DataStreamHandler extends SimpleChannelInboundHandler<Object> {
    private static final String TAG = DataStreamHandler.class.getSimpleName();
    private final LiteHost host;
    private DataHandler reader = new DataHandler(IPFS.BLOCK_SIZE_LIMIT);
    private String lastProtocol;
    @Nullable
    private final Pusher pusher;

    public DataStreamHandler(@NonNull LiteHost host,  @Nullable Pusher pusher) {
        this.host = host;
        this.pusher = pusher;
    }


    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {

        LogUtils.error(TAG, ctx.channel().remoteAddress().toString());

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LogUtils.error(TAG, cause.getClass().getSimpleName());
        ctx.close().get();
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object value) throws Exception {

        QuicChannel quicChannel = (QuicChannel) ctx.channel().parent();

        ByteBuf msg = (ByteBuf) value;
        Objects.requireNonNull(msg);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        msg.readBytes(out, msg.readableBytes());
        byte[] data = out.toByteArray();
        reader.load(data);

        if (reader.isDone()) {
            for (String token : reader.getTokens()) {
                LogUtils.error(TAG, "TOKEN " + token);
                if (token.equals(IPFS.STREAM_PROTOCOL)) {
                    ctx.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
                } else if (token.equals(IPFS.PUSH_PROTOCOL)) {
                    lastProtocol = IPFS.PUSH_PROTOCOL;
                    ctx.writeAndFlush(DataHandler.writeToken(IPFS.PUSH_PROTOCOL));
                } else if (token.equals(IPFS.BITSWAP_PROTOCOL)) {
                    lastProtocol = IPFS.BITSWAP_PROTOCOL;
                    ctx.writeAndFlush(DataHandler.writeToken(IPFS.BITSWAP_PROTOCOL)).get();
                } else if (token.equals(IPFS.IDENTITY_PROTOCOL)) {
                    //lastProtocol = IPFS.IDENTITY_PROTOCOL;
                    ctx.write(DataHandler.writeToken(IPFS.IDENTITY_PROTOCOL));

                    try {
                        IdentifyOuterClass.Identify response =
                                host.createIdentity(quicChannel.remoteAddress());
                        ctx.writeAndFlush(DataHandler.encode(response)).get();
                        ctx.close().get();
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                }
            }


            byte[] message = reader.getMessage();

            if (message != null) {
                switch (lastProtocol) {
                    case IPFS.BITSWAP_PROTOCOL:
                        LogUtils.error(TAG, "Found " + lastProtocol);
                        host.forwardMessage(quicChannel.attr(LiteHost.PEER_KEY).get(),
                                MessageOuterClass.Message.parseFrom(message));
                        break;
                    case IPFS.PUSH_PROTOCOL:
                        LogUtils.error(TAG, "Found " + lastProtocol);
                        host.push(quicChannel.attr(LiteHost.PEER_KEY).get(), message);
                        break;
                    default:
                        throw new Exception("unknown protocol");
                }
                ctx.close();
            }
            reader = new DataHandler(IPFS.BLOCK_SIZE_LIMIT); // TODO
        } else {
            LogUtils.error(TAG, "iteration listener " + data.length + " "
                    + reader.expectedBytes() + " " + ctx.name() + " "
                    + ctx.channel().remoteAddress());
        }
    }



}
