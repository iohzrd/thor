package io.ipfs.host;

import androidx.annotation.NonNull;

import net.luminis.quic.stream.QuicStream;

import java.util.concurrent.atomic.AtomicReference;

import bitswap.pb.MessageOuterClass;
import identify.pb.IdentifyOuterClass;
import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.utils.DataHandler;


public class ConnectionStreamHandler extends ConnectionChannelHandler {
    private static final String TAG = ConnectionStreamHandler.class.getSimpleName();
    private final LiteHost host;
    private final DataHandler reader = new DataHandler(IPFS.PROTOCOL_READER_LIMIT);
    private final AtomicReference<String> protocol = new AtomicReference<>();
    private long time = System.currentTimeMillis();

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


                LogUtils.debug(TAG, "Token " + token + " Connection " + connection + " StreamId " + ""
                        + " PeerId " + connection.remoteId());
                protocol.set(token);
                switch (token) {
                    case IPFS.STREAM_PROTOCOL:
                        writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
                        break;
                    case IPFS.PUSH_PROTOCOL:
                        writeAndFlush(DataHandler.writeToken(IPFS.PUSH_PROTOCOL));
                        break;
                    case IPFS.BITSWAP_PROTOCOL:
                        if (host.gatePeer(connection.remoteId())) {
                            writeAndFlush(DataHandler.writeToken(IPFS.NA));
                            close();
                            return;
                        } else {
                            writeAndFlush(DataHandler.writeToken(IPFS.BITSWAP_PROTOCOL));
                        }
                        time = System.currentTimeMillis();
                        break;
                    case IPFS.IDENTITY_PROTOCOL:
                        writeAndFlush(DataHandler.writeToken(IPFS.IDENTITY_PROTOCOL));

                        IdentifyOuterClass.Identify response =
                                host.createIdentity(connection.remoteAddress());
                        writeAndFlush(DataHandler.encode(response));
                        close();
                        return;
                    default:
                        LogUtils.debug(TAG, "Ignore " + token + " Connection " + connection +
                                " StreamId todo " + " PeerId " + connection.remoteId());
                        writeAndFlush(DataHandler.writeToken(IPFS.NA));
                        close();

                        connection.disconnect();// todo rethink
                        return;
                }
            }

            byte[] message = reader.getMessage();

            if (message != null) {
                String lastProtocol = protocol.get();
                if (lastProtocol != null) {
                    switch (lastProtocol) {
                        case IPFS.BITSWAP_PROTOCOL:
                            host.forwardMessage(
                                    connection.remoteId(),
                                    MessageOuterClass.Message.parseFrom(message));

                            LogUtils.debug(TAG, "Time " + (System.currentTimeMillis() - time) +
                                    " Connection " + connection + " StreamId todo" +
                                    " PeerId " + connection.remoteId() +
                                    " Protected " + host.isProtected(connection.remoteId()));
                            break;
                        case IPFS.PUSH_PROTOCOL:
                            host.push(connection.remoteId(), message);
                            break;
                        default:
                            throw new Exception("unknown protocol");
                    }
                }
            }
        } else {
            LogUtils.error(TAG, "iteration listener " + msg.length + " "
                    + reader.expectedBytes() + " " + connection.remoteAddress());
        }
    }
}
