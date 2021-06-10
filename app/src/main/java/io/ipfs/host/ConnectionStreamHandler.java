package io.ipfs.host;

import androidx.annotation.NonNull;

import net.luminis.quic.stream.QuicStream;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import identify.pb.IdentifyOuterClass;
import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.utils.DataHandler;


public class ConnectionStreamHandler extends ConnectionChannelHandler {
    private static final String TAG = ConnectionStreamHandler.class.getSimpleName();
    private final LiteHost host;
    private final DataHandler reader = new DataHandler(IPFS.PROTOCOL_READER_LIMIT);

    public ConnectionStreamHandler(@NonNull Connection connection,
                                   @NonNull QuicStream quicStream,
                                   @NonNull LiteHost host) {
        super(connection, quicStream);
        this.host = host;

    }



    public void exceptionCaught(@NonNull Connection connection, @NonNull Throwable cause) {
        LogUtils.error(TAG, cause.getClass().getSimpleName());
        try {
            close();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    public void channelRead0(@NonNull Connection connection, @NonNull byte[] msg) throws Exception {

        LogUtils.error(TAG, "PeerId " + connection.remoteId());

        reader.load(msg);

        if (reader.isDone()) {
            for (String token : reader.getTokens()) {
                if (token.equals(IPFS.STREAM_PROTOCOL)) {
                    writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
                } else if (token.equals(IPFS.IDENTITY_PROTOCOL)) {
                    writeAndFlush(DataHandler.writeToken(IPFS.IDENTITY_PROTOCOL));

                    IdentifyOuterClass.Identify response =
                            host.createIdentity(connection.remoteAddress());
                    writeAndFlush(DataHandler.encode(response));
                    close();

                } else {
                    LogUtils.debug(TAG, token);
                    writeAndFlush(DataHandler.writeToken(IPFS.NA));
                    close();
                }
            }
            /*
            byte[] message = reader.getMessage();

            if (message != null) {
                String lastProtocol = protocols.get(streamId);
                if (lastProtocol != null) {
                    switch (lastProtocol) {
                        case IPFS.BITSWAP_PROTOCOL:
                            host.forwardMessage(
                                    quicChannel.attr(LiteHost.PEER_KEY).get(),
                                    MessageOuterClass.Message.parseFrom(message));

                            LogUtils.debug(TAG, "Time " + (System.currentTimeMillis() - time) +
                                    " Connection " + connection + " StreamId " + streamId +
                                    " PeerId " + quicChannel.attr(LiteHost.PEER_KEY).get() +
                                    " Protected " + host.isProtected(quicChannel.attr(LiteHost.PEER_KEY).get()));
                            break;
                        case IPFS.PUSH_PROTOCOL:
                            host.push(quicChannel.attr(LiteHost.PEER_KEY).get(), message);
                            break;
                        default:
                            throw new Exception("unknown protocol");
                    }
                }
            }

             */
        } else {
            LogUtils.error(TAG, "iteration listener " + msg.length + " "
                    + reader.expectedBytes() + " " + connection.remoteAddress());
        }
    }
}
