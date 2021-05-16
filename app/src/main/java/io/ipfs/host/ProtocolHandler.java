package io.ipfs.host;

import androidx.annotation.NonNull;

import com.google.protobuf.MessageLite;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;

import bitswap.pb.MessageOuterClass;
import dht.pb.Dht;
import identify.pb.IdentifyOuterClass;
import io.LogUtils;
import io.ipfs.IPFS;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.incubator.codec.quic.QuicChannel;


public class ProtocolHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final String TAG = ProtocolHandler.class.getSimpleName();

    private final int size;

    private final String protocol;
    @NonNull
    private final CompletableFuture<MessageLite> request;
    @NonNull
    private final LiteHost host;
    private DataHandler reader;

    public ProtocolHandler(@NonNull LiteHost host,
                           @NonNull CompletableFuture<MessageLite> request,
                           @NonNull String protocol) {
        this.host = host;
        this.protocol = protocol;

        this.request = request;

        if (protocol.equals(IPFS.IDENTITY_PROTOCOL) ||
                protocol.equals(IPFS.KAD_DHT_PROTOCOL)) {
            size = 25000; // TODO
        } else {
            size = IPFS.BLOCK_SIZE_LIMIT;
        }
        this.reader = new DataHandler(size);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        LogUtils.error(TAG, "" + cause);
        ctx.close().get();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        LogUtils.error(TAG, "channelUnregistered");

        QuicChannel quicChannel = (QuicChannel) ctx.channel().parent();

        host.removeStream(quicChannel, protocol);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg)
            throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        msg.readBytes(out, msg.readableBytes());
        byte[] data = out.toByteArray();
        reader.load(data);

        if (reader.isDone()) {

            byte[] message = reader.getMessage();
            if (message != null) {
                switch (protocol) {
                    case IPFS.RELAY_PROTOCOL:
                        LogUtils.debug(TAG, "Found " + protocol);
                        request.complete(relay.pb.Relay.CircuitRelay.parseFrom(message));
                        break;
                    case IPFS.IDENTITY_PROTOCOL:
                        LogUtils.debug(TAG, "Found " + protocol);
                        request.complete(IdentifyOuterClass.Identify.parseFrom(message));
                        break;
                    case IPFS.BIT_SWAP_PROTOCOL:
                        LogUtils.debug(TAG, "Found " + protocol);
                        request.complete(MessageOuterClass.Message.parseFrom(message));
                        break;
                    case IPFS.KAD_DHT_PROTOCOL:
                        LogUtils.debug(TAG, "Found " + protocol);
                        request.complete(Dht.Message.parseFrom(message));
                        break;
                    default:
                        throw new Exception("unsupported protocol " + protocol);
                }
            }
            reader = new DataHandler(size);
        } else {
            LogUtils.debug(TAG, "iteration " + data.length + " "
                    + reader.expectedBytes() + " " + ctx.name() + " "
                    + ctx.channel().remoteAddress());
        }
    }
}
