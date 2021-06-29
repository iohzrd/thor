package threads.lite.host;

import androidx.annotation.NonNull;

import net.luminis.quic.QuicClientConnection;
import net.luminis.quic.stream.QuicStream;

import bitswap.pb.MessageOuterClass;
import identify.pb.IdentifyOuterClass;
import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.PeerId;
import threads.lite.utils.DataHandler;


public class StreamHandler extends QuicStreamHandler {
    private static final String TAG = StreamHandler.class.getSimpleName();
    private final LiteHost host;
    @NonNull
    private final QuicClientConnection connection;
    @NonNull
    private final DataHandler reader = new DataHandler(IPFS.MESSAGE_SIZE_MAX);
    @NonNull
    private final PeerId peerId;
    private volatile String protocol = null;
    private long time = System.currentTimeMillis();


    public StreamHandler(@NonNull QuicClientConnection connection,
                         @NonNull QuicStream quicStream,
                         @NonNull PeerId peerId,
                         @NonNull LiteHost host) {
        super(quicStream);
        this.connection = connection;
        this.host = host;
        this.peerId = peerId;
        new Thread(this::reading).start();
        LogUtils.debug(TAG, "Instance" + " StreamId " + streamId + " PeerId " + peerId);
    }


    public void exceptionCaught(@NonNull Throwable cause) {
        LogUtils.debug(TAG, "Error" + " StreamId " + streamId + " PeerId " + peerId + " " + cause);
        host.disconnect(peerId);
        reader.clear();
    }


    public void channelRead0(@NonNull byte[] msg) throws Exception {

        reader.load(msg);

        if (reader.isDone()) {
            for (String token : reader.getTokens()) {

                LogUtils.debug(TAG, "Token " + token + " StreamId " + streamId + " PeerId " + peerId);

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
                        if (host.gatePeer(peerId)) {
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
                                host.createIdentity(connection.getRemoteAddress());
                        writeAndFlush(DataHandler.encode(response));
                        closeInputStream();
                        closeOutputStream();
                        return;
                    default:
                        LogUtils.debug(TAG, "Ignore " + token +
                                " StreamId " + streamId + " PeerId " + peerId);
                        writeAndFlush(DataHandler.writeToken(IPFS.NA));
                        closeInputStream();
                        closeOutputStream();
                        host.disconnect(peerId);
                        return;
                }
            }
            byte[] message = reader.getMessage();

            if (message != null) {
                if (protocol != null) {
                    switch (protocol) {
                        case IPFS.BITSWAP_PROTOCOL:
                            host.forwardMessage(peerId,
                                    MessageOuterClass.Message.parseFrom(message));

                            LogUtils.debug(TAG, "Time " + (System.currentTimeMillis() - time) +
                                    " StreamId " + streamId + " PeerId " + peerId +
                                    " Protected " + host.isProtected(peerId));
                            closeInputStream();
                            break;
                        case IPFS.PUSH_PROTOCOL:
                            host.push(peerId, message);
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
                    + reader.expectedBytes() + " StreamId " + streamId + " PeerId " + peerId +
                    " Tokens " + reader.getTokens().toString());
        }
    }
}
