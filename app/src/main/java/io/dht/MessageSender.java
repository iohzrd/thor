package io.dht;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import io.Closeable;
import io.LogUtils;
import io.ipfs.ClosedException;
import io.ipfs.ProtocolNotSupported;
import io.libp2p.core.NoSuchRemoteProtocolException;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
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
            @NonNull Closeable ctx, @NonNull DhtProtos.Message message) throws ClosedException, ProtocolNotSupported {


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
            LogUtils.error(TAG, "success " + p.toBase58());
            return result;

        } catch (Throwable throwable) {
            if (ctx.isClosed()) {
                throw new ClosedException();
            }
            Throwable cause = throwable.getCause();
            if (cause instanceof NoSuchRemoteProtocolException) {
                throw new ProtocolNotSupported(); // TODO do not introduce extra exception use NoSuchRemoteProtocolException
            }
            // TODO
            if (cause instanceof NothingToCompleteException) {
                try {
                    Collection<Multiaddr> cols = dht.host.getAddressBook().get(p).get();
                    if (cols != null) {
                        for (Multiaddr col :
                                cols) {
                            LogUtils.error(TAG, col.toString());
                        }
                    }
                } catch (Throwable throwable1) {
                    LogUtils.error(TAG, throwable1);
                }

            }
            LogUtils.error(TAG, throwable);
            throw new RuntimeException(throwable); // TODO
        }

    }

}
