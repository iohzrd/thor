package io.dht;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

import io.LogUtils;
import io.ipfs.multihash.Multihash;
import io.libp2p.core.ConnectionClosedException;
import io.libp2p.core.P2PChannel;
import io.libp2p.core.Stream;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.multistream.ProtocolDescriptor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.protos.dht.DhtProtos;
import kotlin.NotImplementedError;

public class DhtProtocol implements ProtocolBinding<DhtProtocol.DhtController> {

    private static final String TAG = DhtProtocol.class.getSimpleName();

    @NotNull
    @Override
    public ProtocolDescriptor getProtocolDescriptor() {
        return new ProtocolDescriptor(KadDHT.Protocol);
    }

    @NotNull
    @Override
    public CompletableFuture<DhtController> initChannel(@NotNull P2PChannel ch, @NotNull String selectedProtocol) {
        CompletableFuture<DhtController> ret = new CompletableFuture<>();
        ChannelHandler handler;
        if (ch.isInitiator()) {
            handler = new ClientHandler((Stream) ch, ret);
        } else {
            handler = new ServerHandler();
        }

        ch.pushHandler(handler);
        return ret;
    }


    public interface DhtController {
        CompletableFuture<DhtProtos.Message> sendRequest(@NonNull DhtProtos.Message pmes) throws NotImplementedError;
    }

    abstract static class DhtHandler extends SimpleChannelInboundHandler<ByteBuf> implements DhtController {
    }

    private static class ServerHandler extends DhtHandler {

        @Override
        public CompletableFuture<DhtProtos.Message> sendRequest(@NonNull DhtProtos.Message pmes) throws NotImplementedError {
            LogUtils.error(TAG, "sendRequest");
            throw new NotImplementedError();
            //return null;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {

            LogUtils.error(TAG, "channelRead0");
            DhtProtos.Message pmes = null; // TODO
            ctx.writeAndFlush(Unpooled.buffer().writeBytes(pmes.toByteArray()));
            ctx.close();
        }
    }

    private static class ClientHandler extends DhtHandler {
        private final CompletableFuture<DhtProtos.Message> resFuture = new CompletableFuture<>();
        private final CompletableFuture<DhtController> activationFut;
        private final Stream stream;

        public ClientHandler(@NonNull Stream stream, @NonNull CompletableFuture<DhtController> fut) {
            this.stream = stream;
            this.activationFut = fut;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            activationFut.complete(this);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            activationFut.completeExceptionally(new ConnectionClosedException());
            resFuture.completeExceptionally(new ConnectionClosedException());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                throws Exception {
            LogUtils.error(TAG, cause);
            activationFut.completeExceptionally(cause);
            resFuture.completeExceptionally(cause);
        }

        @Override
        public CompletableFuture<DhtProtos.Message> sendRequest(@NonNull DhtProtos.Message pmes) throws NotImplementedError {
            byte[] data = pmes.toByteArray();
            try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                Multihash.putUvarint(buf, data.length);
                buf.write(data);
                stream.writeAndFlush(Unpooled.buffer().writeBytes(buf.toByteArray()));
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
                throw new NotImplementedError("" + throwable.getMessage());
            }
            return resFuture;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {

            // TODO when not all data is in the msg
            byte[] data = new byte[msg.readableBytes()];
            int readerIndex = msg.readerIndex();
            msg.getBytes(readerIndex, data);

            try (InputStream inputStream = new ByteArrayInputStream(data)) {
                long length = Multihash.readVarint(inputStream);

                byte[] content = new byte[(int) length];
                int res = inputStream.read(content);
                DhtProtos.Message message = DhtProtos.Message.parseFrom(content);
                resFuture.complete(message);

            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
                throw new RuntimeException(throwable);
            }
        }
    }
}


