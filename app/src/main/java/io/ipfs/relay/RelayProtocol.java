package io.ipfs.relay;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import kotlin.NotImplementedError;
import relay.pb.Relay;

public class RelayProtocol implements ProtocolBinding<RelayProtocol.RelayController> {
    public static final String Protocol = "/libp2p/circuit/relay/0.1.0";
    private static final String TAG = RelayProtocol.class.getSimpleName();

    @NotNull
    @Override
    public ProtocolDescriptor getProtocolDescriptor() {
        return new ProtocolDescriptor(RelayProtocol.Protocol);
    }

    @NotNull
    @Override
    public CompletableFuture<? extends RelayController> initChannel(@NotNull P2PChannel ch, @NotNull String selectedProtocol) {
        CompletableFuture<RelayProtocol.RelayController> ret = new CompletableFuture<>();
        ChannelHandler handler;
        if (ch.isInitiator()) {
            handler = new RelayProtocol.ClientHandler((Stream) ch, ret);
        } else {
            handler = new RelayProtocol.ServerHandler();
        }

        ch.pushHandler(handler);
        return ret;
    }

    public interface RelayController {
        CompletableFuture<Relay.CircuitRelay> canHop(@NonNull Relay.CircuitRelay pmes) throws NotImplementedError;
    }

    abstract static class Handler extends SimpleChannelInboundHandler<ByteBuf> implements RelayProtocol.RelayController {
    }

    private static class ServerHandler extends RelayProtocol.Handler {


        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            throw new Exception("NOT IMPLEMENTED");
        }

        @Override
        public CompletableFuture<Relay.CircuitRelay> canHop(@NonNull Relay.CircuitRelay pmes) throws NotImplementedError {
            throw new NotImplementedError();
        }
    }


    private static class ClientHandler extends RelayProtocol.Handler {
        private final CompletableFuture<Relay.CircuitRelay> resFuture = new CompletableFuture<>();
        private final CompletableFuture<RelayProtocol.RelayController> activationFut;
        private final Stream stream;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private long expectedLength;

        public ClientHandler(@NonNull Stream stream, @NonNull CompletableFuture<RelayProtocol.RelayController> fut) {
            this.stream = stream;
            this.activationFut = fut;
        }

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
        public void channelActive(ChannelHandlerContext ctx) {
            activationFut.complete(this);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) {
            activationFut.completeExceptionally(new ConnectionClosedException());
            resFuture.completeExceptionally(new ConnectionClosedException());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            activationFut.completeExceptionally(cause);
            resFuture.completeExceptionally(cause);
        }

        @Override
        public CompletableFuture<Relay.CircuitRelay> canHop(@NonNull Relay.CircuitRelay pmes) throws NotImplementedError {
            byte[] data = pmes.toByteArray();
            try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                Multihash.putUvarint(buf, data.length);
                buf.write(data);
                stream.writeAndFlush(Unpooled.buffer().writeBytes(buf.toByteArray()));
                stream.closeWrite().get();
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
            return resFuture;
        }


        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
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
                    Relay.CircuitRelay message = Relay.CircuitRelay.parseFrom(buffer.toByteArray());
                    resFuture.complete(message);
                    buffer.close();
                    ctx.close();
                }

            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }

        }
    }
}
