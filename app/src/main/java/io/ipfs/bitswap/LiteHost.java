package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.Closeable;
import io.dht.ContentRouting;
import io.dht.Providers;
import io.ipfs.ClosedException;
import io.ipfs.IPFS;
import io.ipfs.ProtocolNotSupported;
import io.ipfs.cid.Cid;
import io.libp2p.core.Connection;
import io.libp2p.core.Host;
import io.libp2p.core.NoSuchRemoteProtocolException;
import io.libp2p.core.PeerId;


public class LiteHost implements BitSwapNetwork {

    @NonNull
    private final Host host;
    @NonNull
    private final ContentRouting contentRouting;
    private final List<String> protocols = new ArrayList<>();

    private LiteHost(@NonNull Host host,
                     @NonNull ContentRouting contentRouting,
                     @NonNull List<String> protos) {
        this.host = host;
        this.contentRouting = contentRouting;
        this.protocols.addAll(protos);
    }

    public static BitSwapNetwork NewLiteHost(@NonNull Host host,
                                             @NonNull ContentRouting contentRouting,
                                             @NonNull List<String> protocols) {
        return new LiteHost(host, contentRouting, protocols);
    }

    @Override
    public boolean ConnectTo(@NonNull Closeable closeable, @NonNull PeerId peer, boolean protect) throws ClosedException {
        try {
            CompletableFuture<Connection> future = host.getNetwork().connect(peer);
            // TODO closeable
            return future.get() != null;
        } catch (Throwable throwable) {
            if(closeable.isClosed()){
                throw new ClosedException();
            }
            return false;
        }

    }


    @NonNull
    @Override
    public Set<PeerId> getPeers() {
        Set<PeerId> peerIds = new HashSet<>();
        for (Connection connection: host.getNetwork().getConnections()){
            peerIds.add(connection.secureSession().getRemoteId());
        }

        return peerIds;
    }


    @Override
    public void WriteMessage(@NonNull Closeable closeable, @NonNull PeerId peer,
                             @NonNull BitSwapMessage message, int timeout) throws ClosedException, ProtocolNotSupported {

        try {

            CompletableFuture<Object> ctrl = host.newStream(
                    Collections.singletonList(IPFS.ProtocolBitswap), peer).getController();
            //Object object = ctrl.get(timeout, TimeUnit.SECONDS); // TODO timeout
            Object object = ctrl.get();

            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            BitSwapProtocol.BitSwapController controller = (BitSwapProtocol.BitSwapController) object;
            controller.sendRequest(message);
            /* TODO timeout
            long res = host.WriteMessage(closeable, peer, protocols, data, timeout);
            if (Objects.equals(data.length, res)) {
                throw new RuntimeException("Message not fully written");
            }*/
        } catch (Throwable throwable) {
            Throwable cause = throwable.getCause();
            if (cause instanceof NoSuchRemoteProtocolException) {
                throw new ProtocolNotSupported(); // TODO do not introduce extra exception use NoSuchRemoteProtocolException
            }
            throw new RuntimeException(throwable);
        }
    }



    @Override
    public void FindProvidersAsync(@NonNull Providers providers, @NonNull Cid cid, int number) throws ClosedException {
        contentRouting.FindProvidersAsync(providers, cid, number);
    }

    @Override
    public void Provide(@NonNull Closeable closeable, @NonNull Cid cid) {
        throw new RuntimeException("not supported");
    }
}
