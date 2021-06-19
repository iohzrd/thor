package io.ipfs.push;

import androidx.annotation.NonNull;

import net.luminis.quic.QuicClientConnection;
import net.luminis.quic.stream.QuicStream;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.ipfs.IPFS;
import io.ipfs.host.Connection;
import io.ipfs.utils.DataHandler;

public class PushService {

    public static void notify(@NonNull Connection conn, @NonNull String content) {

        try {
            QuicClientConnection quicChannel = conn.channel();

            CompletableFuture<Void> stream = new CompletableFuture<>();
            QuicStream streamChannel = quicChannel.createStream(true);
            PushSend pushSend = new PushSend(streamChannel, stream);

            // TODO streamChannel.updatePriority(new QuicStreamPriority(IPFS.PRIORITY_HIGH, false));

            pushSend.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
            pushSend.writeAndFlush(DataHandler.writeToken(IPFS.PUSH_PROTOCOL));


            stream.get(IPFS.CONNECT_TIMEOUT, TimeUnit.SECONDS);
            // todo exception
            pushSend.writeAndFlush(DataHandler.encode(content.getBytes()));
            pushSend.closeOutputStream();
            pushSend.closeInputStream();

        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
}
