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
    private final DataHandler reader = new DataHandler(IPFS.MESSAGE_SIZE_MAX);
    private final AtomicReference<String> protocol = new AtomicReference<>();
    private long time = System.currentTimeMillis();

    public ConnectionStreamHandler(@NonNull Connection connection,
                                   @NonNull QuicStream quicStream,
                                   @NonNull LiteHost host) {
        super(connection, quicStream);
        this.host = host;
        LogUtils.debug(TAG, "Instance" + " StreamId " + streamId + " PeerId " + connection.remoteId());
    }


    public void exceptionCaught(@NonNull Connection connection, @NonNull Throwable cause) {
        LogUtils.debug(TAG, "" + cause);
        closeInputStream();
        closeOutputStream();
    }


    public void channelRead0(@NonNull Connection connection, @NonNull byte[] msg) throws Exception {

        reader.load(msg);

        if (reader.isDone()) {
            for (String token : reader.getTokens()) {

                // TODO check token happens only once
                LogUtils.debug(TAG, "Token " + token + " Connection " + connection +
                        " StreamId " + streamId + " PeerId " + connection.remoteId());

                switch (token) {
                    case IPFS.STREAM_PROTOCOL:
                        writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
                        break;
                    case IPFS.PUSH_PROTOCOL:
                        protocol.set(token);
                        writeAndFlush(DataHandler.writeToken(IPFS.PUSH_PROTOCOL));
                        closeOutputStream();
                        break;
                    case IPFS.BITSWAP_PROTOCOL:
                        protocol.set(token);
                        if (host.gatePeer(connection.remoteId())) {
                            writeAndFlush(DataHandler.writeToken(IPFS.NA));
                            closeInputStream();
                            closeOutputStream();
                            return;
                        } else {
                            writeAndFlush(DataHandler.writeToken(IPFS.BITSWAP_PROTOCOL));
                            closeOutputStream();
                        }
                        time = System.currentTimeMillis();
                        break;
                    case IPFS.IDENTITY_PROTOCOL:
                        writeAndFlush(DataHandler.writeToken(IPFS.IDENTITY_PROTOCOL));

                        IdentifyOuterClass.Identify response =
                                host.createIdentity(connection.remoteAddress());
                        writeAndFlush(DataHandler.encode(response));
                        closeInputStream();
                        closeOutputStream();
                        return;
                    default:
                        LogUtils.debug(TAG, "Ignore " + token + " Connection " + connection +
                                " StreamId " + streamId + " PeerId " + connection.remoteId());
                        writeAndFlush(DataHandler.writeToken(IPFS.NA));
                        closeInputStream();
                        closeOutputStream();
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

                            LogUtils.error(TAG, "Time " + (System.currentTimeMillis() - time) +
                                    " Connection " + connection + " StreamId " + streamId +
                                    " PeerId " + connection.remoteId() +
                                    " Protected " + host.isProtected(connection.remoteId()));
                            closeInputStream();
                            break;
                        case IPFS.PUSH_PROTOCOL:
                            host.push(connection.remoteId(), message);
                            closeInputStream();
                            break;
                        default:
                            throw new Exception("unknown protocol");
                    }
                } else {
                    throw new Exception("unknown protocol");
                }
            }
        } else {
            LogUtils.debug(TAG, "iteration " + protocol.get() + " " + reader.hasRead() + " "
                    + reader.expectedBytes() + " " + connection.remoteAddress()
                    + " StreamId " + streamId + " PeerId " + connection.remoteId());
        }
    }
}
