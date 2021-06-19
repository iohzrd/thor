package io.ipfs.host;

import androidx.annotation.NonNull;

import net.luminis.quic.stream.QuicStream;

import bitswap.pb.MessageOuterClass;
import identify.pb.IdentifyOuterClass;
import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.utils.DataHandler;


public class StreamHandler extends ConnectionChannelHandler {
    private static final String TAG = StreamHandler.class.getSimpleName();
    private final LiteHost host;
    @NonNull
    private final Connection connection;
    @NonNull
    private final DataHandler reader = new DataHandler(IPFS.MESSAGE_SIZE_MAX);
    private volatile String protocol = null;
    private long time = System.currentTimeMillis();


    public StreamHandler(@NonNull Connection connection,
                         @NonNull QuicStream quicStream,
                         @NonNull LiteHost host) {
        super(quicStream);
        this.connection = connection;
        this.host = host;
        new Thread(this::reading).start();
        LogUtils.debug(TAG, "Instance" + " StreamId " + streamId + " PeerId " + connection.remoteId());
    }


    public void exceptionCaught(@NonNull Throwable cause) {
        LogUtils.debug(TAG, "Error" + " StreamId " + streamId + " PeerId " + connection.remoteId() + " " + cause);
        connection.disconnect();
        reader.clear();
    }


    public void channelRead0(@NonNull byte[] msg) throws Exception {

        reader.load(msg);

        if (reader.isDone()) {
            for (String token : reader.getTokens()) {

                LogUtils.debug(TAG, "Token " + token + " StreamId " + streamId + " PeerId " + connection.remoteId());

                protocol = token;
                switch (token) {
                    case IPFS.STREAM_PROTOCOL:
                        writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
                        break;
                    case IPFS.PUSH_PROTOCOL:
                        writeAndFlush(DataHandler.writeToken(IPFS.PUSH_PROTOCOL));
                        closeOutputStream();
                        break;
                    case IPFS.BITSWAP_PROTOCOL:
                        // TODO check if correct
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
                        LogUtils.debug(TAG, "Ignore " + token +
                                " StreamId " + streamId + " PeerId " + connection.remoteId());
                        writeAndFlush(DataHandler.writeToken(IPFS.NA));
                        closeInputStream();
                        closeOutputStream();
                        connection.disconnect();
                        return;
                }
            }
            byte[] message = reader.getMessage();

            if (message != null) {
                if (protocol != null) {
                    switch (protocol) {
                        case IPFS.BITSWAP_PROTOCOL:
                            host.forwardMessage(connection.remoteId(),
                                    MessageOuterClass.Message.parseFrom(message));

                            LogUtils.debug(TAG, "Time " + (System.currentTimeMillis() - time) +
                                    " StreamId " + streamId + " PeerId " + connection.remoteId() +
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

            LogUtils.debug(TAG, "Iteration " + protocol + " " + reader.hasRead() + " "
                    + reader.expectedBytes() + " " + connection.remoteAddress()
                    + " StreamId " + streamId + " PeerId " + connection.remoteId() +
                    " Tokens " + reader.getTokens().toString());
        }
    }
}
