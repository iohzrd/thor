package io.ipfs.push;

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

public class PushProtocol implements ProtocolBinding<PushProtocol.PushController> {
    private static final String TAG = PushProtocol.class.getSimpleName();
    private final String protocol = "/ipfs/push/1.0.0";
    private final PushReceiver pushReceiver;

    public PushProtocol(@NonNull PushReceiver pushReceiver) {
        this.pushReceiver = pushReceiver;
    }

    @NotNull
    @Override
    public ProtocolDescriptor getProtocolDescriptor() {
        return new ProtocolDescriptor(protocol);
    }

    @NotNull
    @Override
    public CompletableFuture<PushController> initChannel(@NotNull P2PChannel ch, @NotNull String selectedProtocol) {
        CompletableFuture<PushController> ret = new CompletableFuture<>();
        ChannelHandler handler;
        if (ch.isInitiator()) {
            handler = new ClientHandler((Stream) ch, ret);
        } else {
            handler = new ServerHandler((Stream) ch, pushReceiver);
        }

        ch.pushHandler(handler);
        return ret;
    }


    public interface PushController {
        boolean push(@NonNull String message) throws NotImplementedError;
    }

    abstract static class DhtHandler extends SimpleChannelInboundHandler<ByteBuf> implements PushController {
    }

    private static class ServerHandler extends DhtHandler {
        private final PushReceiver pushReceiver;
        private final Stream stream;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private long expectedLength;


        private ServerHandler(@NonNull Stream stream, @NonNull PushReceiver pushReceiver) {
            this.stream = stream;
            this.pushReceiver = pushReceiver;
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
                return pushReceiver.acceptPusher(stream.remotePeerId());
            } catch (Throwable ignore) {
                return super.acceptInboundMessage(msg);
            }
        }


        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                throws Exception {
            LogUtils.error(TAG, cause);
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
                    pushReceiver.pushMessage(stream.remotePeerId(), buffer.toByteArray());
                    buffer.close();
                    ctx.close();
                }

            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
                throw new RuntimeException(throwable);
            }
        }

        @Override
        public boolean push(@NonNull String message) throws NotImplementedError {
            throw new NotImplementedError();
        }
    }

    private static class ClientHandler extends DhtHandler {
        private final CompletableFuture<PushController> activationFut;
        private final Stream stream;

        public ClientHandler(@NonNull Stream stream, @NonNull CompletableFuture<PushController> fut) {
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
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            LogUtils.error(TAG, "TODO channel read ");
            throw new RuntimeException("TODO maybe");
        }

        @Override
        public boolean push(@NonNull String message) throws NotImplementedError {
            byte[] data = message.getBytes();
            try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                Multihash.putUvarint(buf, data.length);
                buf.write(data);
                stream.writeAndFlush(Unpooled.buffer().writeBytes(buf.toByteArray()));
                stream.closeWrite().get();
                return true;
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
                return false;
            }

        }
    }
}


