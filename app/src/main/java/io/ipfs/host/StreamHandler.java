package io.ipfs.host;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import bitswap.pb.MessageOuterClass;
import identify.pb.IdentifyOuterClass;
import io.LogUtils;
import io.ipfs.IPFS;
import io.quic.QuicheWrapper;
import io.netty.buffer.ByteBuf;
import io.netty.incubator.codec.quic.QuicChannel;
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

            if(fin) {
                QuicheWrapper.streamShutdown(connection,
                        streamId, true, true, 0);
                close(streamId);
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable); // TODO close etc
            close(streamId);
        } finally {
            ReferenceCountUtil.release(byteBuf);
        }
    }


    private void close(long streamId) {
        QuicheWrapper.streamShutdown(connection, streamId, true, true, 0);

        protocols.remove(streamId);
        handlers.remove(streamId);
    }


    protected void channelRead0(QuicChannel quicChannel, long streamId, ByteBuf msg) throws Exception {


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
                        QuicheWrapper.write(connection, streamId, DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
                        break;
                    case IPFS.PUSH_PROTOCOL:
                        QuicheWrapper.writeAndFlush(connection, streamId, DataHandler.writeToken(IPFS.PUSH_PROTOCOL));
                        QuicheWrapper.streamShutdown(connection, streamId, false, true, 0);
                        break;
                    case IPFS.BITSWAP_PROTOCOL:
                        QuicheWrapper.writeAndFlush(connection, streamId, DataHandler.writeToken(IPFS.BITSWAP_PROTOCOL));
                        QuicheWrapper.streamShutdown(connection, streamId, false, true, 0);
                        break;
                    case IPFS.IDENTITY_PROTOCOL:
                        QuicheWrapper.write(connection, streamId, DataHandler.writeToken(IPFS.IDENTITY_PROTOCOL));
                        IdentifyOuterClass.Identify response =
                                host.createIdentity(quicChannel.remoteAddress());
                        QuicheWrapper.writeAndFlush(connection, streamId, DataHandler.encode(response));
                        close(streamId);
                        return;
                    default:
                        // LogUtils.error(TAG, "Ignore " + token);
                        QuicheWrapper.streamSendFin(connection, streamId);
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
