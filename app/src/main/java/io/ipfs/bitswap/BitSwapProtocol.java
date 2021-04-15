package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

public class BitSwapProtocol implements ProtocolBinding<BitSwapProtocol.BitSwapController> {
    private static final String TAG = BitSwapProtocol.class.getSimpleName();
    private final String protocol;
    private final Receiver receiver;

    public BitSwapProtocol(@NonNull Receiver receiver, @NonNull String protocol) {
        this.protocol = protocol;
        this.receiver = receiver;
    }

    @NotNull
    @Override
    public ProtocolDescriptor getProtocolDescriptor() {
        return new ProtocolDescriptor(protocol);
    }

    @NotNull
    @Override
    public CompletableFuture<BitSwapController> initChannel(@NotNull P2PChannel ch, @NotNull String selectedProtocol) {
        CompletableFuture<BitSwapController> ret = new CompletableFuture<>();
        ChannelHandler handler;
        if (ch.isInitiator()) {
            handler = new ClientHandler((Stream) ch, ret);
        } else {
            handler = new ServerHandler((Stream) ch, receiver);
        }

        ch.pushHandler(handler);
        return ret;
    }


    public interface BitSwapController {
        void sendRequest(@NonNull BitSwapMessage pmes) throws NotImplementedError;
    }

    abstract static class DhtHandler extends SimpleChannelInboundHandler<ByteBuf> implements BitSwapController {
    }

    private static class ServerHandler extends DhtHandler {
        private final Receiver receiver;
        private final Stream stream;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private long expectedLength;


        private ServerHandler(@NonNull Stream stream, @NonNull Receiver receiver) {
            this.stream = stream;
            this.receiver = receiver;
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

        public boolean acceptInboundMessage(Object msg) throws Exception {
            try {
                return !receiver.GatePeer(stream.remotePeerId());
            } catch (Throwable ignore) {
                return super.acceptInboundMessage(msg);
            }
        }

        @Override
        public void sendRequest(@NonNull BitSwapMessage message) throws NotImplementedError {
            throw new NotImplementedError();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                throws Exception {
            receiver.ReceiveError(stream.remotePeerId(),
                    stream.getProtocol().get(), " " + cause.getMessage());
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

                if (buffer.size() >= expectedLength) {
                    BitSwapMessage received = BitSwapMessage.fromData(buffer.toByteArray());
                    String protocol = stream.getProtocol().get();
                    Executors.newSingleThreadExecutor().execute(
                            () -> receiver.ReceiveMessage(stream.remotePeerId(), protocol, received));
                    buffer.close();
                    ctx.close();
                }

            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
                throw new RuntimeException(throwable);
            }
        }
    }

    private static class ClientHandler extends DhtHandler {
        private final CompletableFuture<BitSwapController> activationFut;
        private final Stream stream;

        public ClientHandler(@NonNull Stream stream, @NonNull CompletableFuture<BitSwapController> fut) {
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
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                throws Exception {
            LogUtils.error(TAG, cause);
            activationFut.completeExceptionally(cause);
        }

        @Override
        public void sendRequest(@NonNull BitSwapMessage pmes) throws NotImplementedError {
            try {
                byte[] data = pmes.ToNetV1();
                stream.writeAndFlush(Unpooled.buffer().writeBytes(data));
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
                throw new NotImplementedError("" + throwable.getMessage());
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            LogUtils.error(TAG, "TODO channel read ");
            throw new RuntimeException("TODO maybe");
        }
    }
}


