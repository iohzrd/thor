package io.ipfs.host;

import androidx.annotation.NonNull;

import net.luminis.quic.stream.QuicStream;

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
        } else {
            LogUtils.error(TAG, "iteration listener " + msg.length + " "
                    + reader.expectedBytes() + " " + connection.remoteAddress());
        }
    }
}
