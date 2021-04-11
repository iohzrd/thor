package io.dht;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import io.LogUtils;
import io.core.Closeable;
import io.core.ClosedException;
import io.core.ConnectionFailure;
import io.core.ProtocolNotSupported;
import io.libp2p.core.ConnectionClosedException;
import io.libp2p.core.NoSuchRemoteProtocolException;
import io.libp2p.core.PeerId;
import io.libp2p.etc.types.NothingToCompleteException;
import io.netty.handler.timeout.ReadTimeoutException;
import io.protos.dht.DhtProtos;

public class MessageSender {
    private static final String TAG = MessageSender.class.getSimpleName();
    private final PeerId p;
    private final KadDHT dht;

    public MessageSender(@NonNull PeerId p, @NonNull KadDHT dht) {
        this.p = p;
        this.dht = dht;
    }

    public synchronized DhtProtos.Message SendRequest(
            @NonNull Closeable ctx, @NonNull DhtProtos.Message message)
            throws ClosedException, ProtocolNotSupported, ConnectionFailure {


        if (ctx.isClosed()) {
            throw new ClosedException();
        }

        try {
            CompletableFuture<Object> ctrl = dht.host.newStream(
                    Collections.singletonList(KadDHT.Protocol), p).getController();

            Object object = ctrl.get(); // TODO timeout

            if (ctx.isClosed()) {
                throw new ClosedException();
            }

            DhtProtocol.DhtController dhtController = (DhtProtocol.DhtController) object;
            return dhtController.sendRequest(message).get();

        } catch (Throwable throwable) {
            if (ctx.isClosed()) {
                throw new ClosedException();
            }
            Throwable cause = throwable.getCause();
            if (cause instanceof NoSuchRemoteProtocolException) {
                throw new ProtocolNotSupported();
            }
            if (cause instanceof NothingToCompleteException) {
                throw new ConnectionFailure();
            }
            if (cause instanceof ConnectionClosedException) {
                throw new ConnectionFailure();
            }
            if (cause instanceof ReadTimeoutException) {
                throw new ConnectionFailure();
            }
            LogUtils.error(TAG, throwable);
            throw new RuntimeException(throwable); // TODO
        }

    }

}
