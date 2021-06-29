package threads.lite.relay;

import androidx.annotation.NonNull;

import net.luminis.quic.stream.QuicStream;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import relay.pb.Relay;
import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.core.ProtocolIssue;
import threads.lite.host.QuicStreamHandler;
import threads.lite.utils.DataHandler;

public class RelayRequest extends QuicStreamHandler {
    private static final String TAG = RelayRequest.class.getSimpleName();
    @NonNull
    private final CompletableFuture<Relay.CircuitRelay> request;

    private final DataHandler reader = new DataHandler(4096);

    public RelayRequest(@NonNull QuicStream quicStream,
                        @NonNull CompletableFuture<relay.pb.Relay.CircuitRelay> request) {
        super(quicStream);
        this.request = request;
        new Thread(this::reading).start();
    }


    public void exceptionCaught(@NonNull Throwable cause) {
        LogUtils.debug(TAG, "" + cause);
        request.completeExceptionally(cause);
        reader.clear();
    }


    public void channelRead0(@NonNull byte[] msg)
            throws Exception {

        reader.load(msg);

        if (reader.isDone()) {

            for (String token : reader.getTokens()) {
                LogUtils.verbose(TAG, "request " + token);
                if (Objects.equals(token, IPFS.RELAY_PROTOCOL)) {
                    LogUtils.debug(TAG, "request " + token);
                } else if (!Objects.equals(token, IPFS.STREAM_PROTOCOL)) {
                    throw new ProtocolIssue();
                }
            }

            byte[] message = reader.getMessage();

            if (message != null) {
                request.complete(Relay.CircuitRelay.parseFrom(message));
                closeInputStream();
            }
        } else {
            LogUtils.debug(TAG, "iteration " + reader.hasRead() + " " + reader.expectedBytes());
        }
    }
}
