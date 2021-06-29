package threads.lite.bitswap;

import androidx.annotation.NonNull;

import net.luminis.quic.stream.QuicStream;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.PeerId;
import threads.lite.core.ProtocolIssue;
import threads.lite.host.QuicStreamHandler;
import threads.lite.utils.DataHandler;

public class BitSwapSend extends QuicStreamHandler {

    private static final String TAG = BitSwapSend.class.getSimpleName();

    @NonNull
    private final DataHandler reader = new DataHandler(IPFS.PROTOCOL_READER_LIMIT);

    @NonNull
    private final CompletableFuture<BitSwapSend> done;
    @NonNull
    private final PeerId peerId;

    public BitSwapSend(@NonNull PeerId peerId,
                       @NonNull QuicStream quicStream,
                       @NonNull CompletableFuture<BitSwapSend> done) {
        super(quicStream);
        this.peerId = peerId;
        this.done = done;
        new Thread(this::reading).start();
    }


    public void exceptionCaught(@NonNull Throwable cause) {
        LogUtils.debug(TAG, "" + cause);
        done.completeExceptionally(cause);
        reader.clear();
    }

    public void channelRead0(@NonNull byte[] msg)
            throws Exception {

        reader.load(msg);

        if (reader.isDone()) {
            for (String received : reader.getTokens()) {
                if (Objects.equals(received, IPFS.BITSWAP_PROTOCOL)) {
                    closeInputStream();
                    done.complete(this);
                } else if (!Objects.equals(received, IPFS.STREAM_PROTOCOL)) {
                    LogUtils.debug(TAG, "NOT SUPPORTED " + received);
                    closeInputStream();
                    throw new ProtocolIssue();
                }
            }
        } else {
            LogUtils.error(TAG, "iteration " + reader.hasRead() + " "
                    + reader.expectedBytes() + " StreamId " + streamId + " PeerId " + peerId);
        }
    }
}
