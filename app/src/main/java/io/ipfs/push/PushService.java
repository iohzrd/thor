package io.ipfs.push;

import androidx.annotation.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.ipfs.IPFS;
import io.ipfs.host.Connection;
import io.ipfs.utils.DataHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamPriority;
import io.netty.incubator.codec.quic.QuicStreamType;

public class PushService {

    public static void notify(@NonNull Connection conn, @NonNull String content) {

        try {
            QuicChannel quicChannel = conn.channel();

            CompletableFuture<QuicStreamChannel> stream = new CompletableFuture<>();
            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new PushSend(stream)).sync().get();

            streamChannel.updatePriority(new QuicStreamPriority(IPFS.PRIORITY_HIGH, false));

            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.PUSH_PROTOCOL));


            QuicStreamChannel channel = stream.get(IPFS.CONNECT_TIMEOUT, TimeUnit.SECONDS);

            channel.writeAndFlush(DataHandler.encode(content.getBytes())).addListener(
                    future -> channel.close().get());

        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
}
