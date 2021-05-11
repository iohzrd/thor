package io.ipfs.host;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import bitswap.pb.MessageOuterClass;
import identify.pb.IdentifyOuterClass;
import io.LogUtils;
import io.ipfs.IPFS;
import io.libp2p.core.PeerId;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelPromise;
import io.netty.incubator.codec.quic.DirectIoByteBufAllocator;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.Quiche;
import io.netty.util.ReferenceCountUtil;


public class StreamHandler {
    private static final String TAG = StreamHandler.class.getSimpleName();
    private static final ConcurrentHashMap<Long, StreamHandler> streams = new ConcurrentHashMap<>();
    private final long connection;
    private final LiteHost host;
    private final HashMap<Long, DataHandler> handlers = new HashMap<>();
    private final HashMap<Long, String> protocols = new HashMap<>();

    public StreamHandler(@NonNull LiteHost host, long connection) {
        this.host = host;
        this.connection = connection;
    }

    public static StreamHandler getInstance(long connection) {
        StreamHandler streamHandler = streams.get(connection);
        if (streamHandler == null) {
            streamHandler = new StreamHandler(IPFS.HOST, connection);
            streams.put(connection, streamHandler);
        }
        return streamHandler;

    }

    public void channelRead(QuicChannel quicChannel, long streamId, ByteBuf byteBuf, boolean fin) {

        try {
            if( byteBuf.capacity() > 0 ) {
                channelRead0(quicChannel, streamId, byteBuf);
            }

            if(fin){
                streamShutdown(streamId, true, true, 0);
                close(streamId);
                LogUtils.error(TAG, "Fin received " + streamId);
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable); // TODO close etc
            close(streamId);
        } finally {
            ReferenceCountUtil.release(byteBuf);
        }
    }


    private void close(long streamId) {
        streamShutdown(streamId,true, true, 0);

        protocols.remove(streamId);
        handlers.remove(streamId);
    }


    private ByteBuf direct(ByteBuf msg) {
        ByteBuf buffer = (ByteBuf) msg;
        if (!buffer.isDirect()) {
            DirectIoByteBufAllocator allocator = new DirectIoByteBufAllocator(ByteBufAllocator.DEFAULT);
            ByteBuf tmpBuffer = allocator.directBuffer(buffer.readableBytes());
            tmpBuffer.writeBytes(buffer, buffer.readerIndex(), buffer.readableBytes());
            buffer.release();
            msg = tmpBuffer;
            return msg;
        }
        return msg;
    }

    private void writeAndFlush(long streamId, ByteBuf message) {
        ByteBuf msg = direct(message);
        try {
            streamSend(streamId, msg, true);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }


    private void write(long streamId, ByteBuf message) {
        ByteBuf msg = direct(message);
        try {
            streamSend(streamId, msg, false);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }


    int streamSend(long streamId, ByteBuf buffer, boolean fin) {
        if (buffer.nioBufferCount() == 1) {
            return streamSend0(streamId, buffer, fin);
        }
        ByteBuffer[] nioBuffers = buffer.nioBuffers();
        int lastIdx = nioBuffers.length - 1;
        int res = 0;
        for (int i = 0; i < lastIdx; i++) {
            ByteBuffer nioBuffer = nioBuffers[i];
            while (nioBuffer.hasRemaining()) {
                int localRes = streamSend(streamId, nioBuffer, false);
                if (localRes <= 0) {
                    return res;
                }
                res += localRes;

                nioBuffer.position(nioBuffer.position() + localRes);
            }
        }
        int localRes = streamSend(streamId, nioBuffers[lastIdx], fin);
        if (localRes > 0) {
            res += localRes;
        }
        return res;
    }


    void streamShutdown(long streamId, boolean read, boolean write, int err) {

        int res = 0;
        if (read) {
            res |= Quiche.quiche_conn_stream_shutdown(connection, streamId, Quiche.QUICHE_SHUTDOWN_READ, err);
        }
        if (write) {
            res |= Quiche.quiche_conn_stream_shutdown(connection, streamId, Quiche.QUICHE_SHUTDOWN_WRITE, err);
        }

    }

    private int streamSend0(long streamId, ByteBuf buffer, boolean fin) {
        return Quiche.quiche_conn_stream_send(connection, streamId,
                Quiche.memoryAddress(buffer) + buffer.readerIndex(), buffer.readableBytes(), fin);
    }

    private int streamSend(long streamId, ByteBuffer buffer, boolean fin) {
        return Quiche.quiche_conn_stream_send(connection, streamId,
                Quiche.memoryAddress(buffer) + buffer.position(), buffer.remaining(), fin);
    }

    void streamSendFin(long streamId) throws Exception {
        // Just write an empty buffer and set fin to true.
        Quiche.throwIfError(streamSend0(streamId, Unpooled.EMPTY_BUFFER, true));

    }

    protected void channelRead0(QuicChannel quicChannel, long streamId, ByteBuf msg) throws Exception {


        PeerId peerId = quicChannel.attr(LiteHost.PEER_KEY).get();
        if(peerId == null){
            LogUtils.error(TAG, "ERROR " + connection + " " + quicChannel.remoteAddress().toString());
        }

        DataHandler reader = handlers.get(streamId);
        if (reader == null) {
            reader = new DataHandler(IPFS.BLOCK_SIZE_LIMIT);
            handlers.put(streamId, reader);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        msg.readBytes(out, msg.readableBytes());
        byte[] data = out.toByteArray();
        reader.load(data);

        if (reader.isDone()) {
            for (String token : reader.getTokens()) {
                protocols.put(streamId, token);
                switch (token) {
                    case IPFS.STREAM_PROTOCOL:
                        write(streamId, DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
                        break;
                    case IPFS.PUSH_PROTOCOL:
                        writeAndFlush(streamId, DataHandler.writeToken(IPFS.PUSH_PROTOCOL));
                        streamShutdown(streamId,false, true, 0);
                        break;
                    case IPFS.BITSWAP_PROTOCOL:
                        writeAndFlush(streamId, DataHandler.writeToken(IPFS.BITSWAP_PROTOCOL));
                        streamShutdown(streamId,false, true, 0);
                        break;
                    case IPFS.IDENTITY_PROTOCOL:
                        write(streamId, DataHandler.writeToken(IPFS.IDENTITY_PROTOCOL));
                        IdentifyOuterClass.Identify response =
                                host.createIdentity(quicChannel.remoteAddress());
                        writeAndFlush(streamId, DataHandler.encode(response));
                        close(streamId);
                        return;
                    default:
                        // LogUtils.error(TAG, "Ignore " + token);
                        streamSendFin(streamId);
                        close(streamId);
                        return;
                }
            }


            byte[] message = reader.getMessage();

            if (message != null) {
                String lastProtocol = protocols.get(streamId);
                if (lastProtocol != null) {
                    switch (lastProtocol) {
                        case IPFS.BITSWAP_PROTOCOL:
                            host.forwardMessage(quicChannel.attr(LiteHost.PEER_KEY).get(),
                                    MessageOuterClass.Message.parseFrom(message));
                            break;
                        case IPFS.PUSH_PROTOCOL:
                            host.push(quicChannel.attr(LiteHost.PEER_KEY).get(), message);
                            break;
                        default:
                            throw new Exception("unknown protocol");
                    }
                }

                close(streamId);
            }
            handlers.remove(streamId);
        } /* else {
            LogUtils.error(TAG, "Iteration  " + reader.hasRead() + " "
                    + reader.expectedBytes() + " Connection " + connection + " StreamId " + streamId);
        }*/
    }


}
