package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
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
import kotlin.NotImplementedError;

public class BitSwapProtocol implements ProtocolBinding<BitSwapProtocol.BitSwapController> {
    private final String protocol;
    private final Receiver receiver;


    private static final String TAG = BitSwapProtocol.class.getSimpleName();

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

        private ServerHandler(@NonNull Stream stream, @NonNull Receiver receiver) {
            this.stream = stream;
            this.receiver = receiver;
        }

        public boolean acceptInboundMessage(Object msg) throws Exception {
            try {
                return !receiver.GatePeer(stream.remotePeerId());
            } catch (Throwable ignore) {
                return super.acceptInboundMessage(msg);
            }
        }

        @Override
        public void sendRequest(@NonNull BitSwapMessage pmes) throws NotImplementedError {
            LogUtils.error(TAG, "sendRequest");
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

            // TODO when not all data is in the msg
            byte[] data = new byte[msg.readableBytes()];
            int readerIndex = msg.readerIndex();
            msg.getBytes(readerIndex, data);

            try (InputStream inputStream = new ByteArrayInputStream(data)) {
                long length = Multihash.readVarint(inputStream);

                byte[] content = new byte[(int) length];
                int res = inputStream.read(content);
                BitSwapMessage received = BitSwapMessage.fromData(content);
                receiver.ReceiveMessage(stream.remotePeerId(),
                        stream.getProtocol().get(), received);

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
            } finally {
                stream.close(); // TODO check (better first writer close
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            LogUtils.error(TAG, "TODO channel read ");
        }
    }
}


