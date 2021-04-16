package io.dht;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

import dht.pb.Dht;
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
        CompletableFuture<Dht.Message> sendRequest(@NonNull Dht.Message pmes) throws NotImplementedError;

        void sendMessage(@NonNull Dht.Message pmes) throws NotImplementedError;
    }

    abstract static class DhtHandler extends SimpleChannelInboundHandler<ByteBuf> implements DhtController {
    }

    private static class ServerHandler extends DhtHandler {

        @Override
        public CompletableFuture<Dht.Message> sendRequest(@NonNull Dht.Message pmes) throws NotImplementedError {
            throw new NotImplementedError();
        }

        @Override
        public void sendMessage(@NonNull Dht.Message pmes) throws NotImplementedError {
            throw new NotImplementedError();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            throw new Exception("NOT IMPLEMENTED");
        }
    }

    private static class ClientHandler extends DhtHandler {
        private final CompletableFuture<Dht.Message> resFuture = new CompletableFuture<>();
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
        public CompletableFuture<Dht.Message> sendRequest(@NonNull Dht.Message pmes) throws NotImplementedError {
            byte[] data = pmes.toByteArray();
            try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                Multihash.putUvarint(buf, data.length);
                buf.write(data);
                stream.writeAndFlush(Unpooled.buffer().writeBytes(buf.toByteArray()));
                stream.closeWrite().get();
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
                throw new NotImplementedError("" + throwable.getMessage());
            }
            return resFuture;
        }

        @Override
        public void sendMessage(@NonNull Dht.Message pmes) throws NotImplementedError {
            byte[] data = pmes.toByteArray();
            try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                Multihash.putUvarint(buf, data.length);
                buf.write(data);
                stream.writeAndFlush(Unpooled.buffer().writeBytes(buf.toByteArray()));
                stream.close().get();
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
                throw new NotImplementedError("" + throwable.getMessage());
            }
        }

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private long expectedLength;

        public static long copy(InputStream source, OutputStream sink) throws IOException {
            long nread = 0L;
            byte[] buf = new byte[4096];
            int n;
            while ((n = source.read(buf)) > 0) {
                sink.write(buf, 0, n);
                nread += n;
            }
            return nread;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            try {
                if (buffer.size() > 0) {
                    msg.readBytes(buffer, msg.readableBytes());
                } else {
                    byte[] data = new byte[msg.readableBytes()];
                    int readerIndex = msg.readerIndex();
                    msg.getBytes(readerIndex, data);
                    try (InputStream inputStream = new ByteArrayInputStream(data)) {
                        expectedLength = Multihash.readVarint(inputStream);
                        copy(inputStream, buffer);
                    }
                }
                if (buffer.size() == expectedLength) {
                    Dht.Message message = Dht.Message.parseFrom(buffer.toByteArray());
                    resFuture.complete(message);
                    buffer.close();
                    ctx.close();
                }

            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
                throw new RuntimeException(throwable);
            }

        }
    }
}


