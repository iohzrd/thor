package io.dht;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import io.Closeable;
import io.LogUtils;
import io.ipfs.ClosedException;
import io.ipfs.ConnectionNotSupported;
import io.ipfs.ProtocolNotSupported;
import io.libp2p.core.NoSuchRemoteProtocolException;
import io.libp2p.core.PeerId;
import io.libp2p.etc.types.NothingToCompleteException;
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
            throws ClosedException, ProtocolNotSupported, ConnectionNotSupported {


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
            DhtProtos.Message result = dhtController.sendRequest(message).get(); // TODO timeout
            LogUtils.info(TAG, "success " + p.toBase58());
            return result;

        } catch (Throwable throwable) {
            if (ctx.isClosed()) {
                throw new ClosedException();
            }
            Throwable cause = throwable.getCause();
            if (cause instanceof NoSuchRemoteProtocolException) {
                throw new ProtocolNotSupported();
            }
            if (cause instanceof NothingToCompleteException) {
                throw new ConnectionNotSupported();
            }
            LogUtils.error(TAG, throwable);
            throw new RuntimeException(throwable); // TODO
        }

    }

}
