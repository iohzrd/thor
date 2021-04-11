package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.core.Closeable;
import io.LogUtils;
import io.core.ConnectionFailure;
import io.libp2p.AddrInfo;
import io.dht.ContentRouting;
import io.dht.Providers;
import io.core.ClosedException;
import io.ipfs.IPFS;
import io.core.ProtocolNotSupported;
import io.ipfs.cid.Cid;
import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionClosedException;
import io.libp2p.core.Host;
import io.libp2p.core.NoSuchRemoteProtocolException;
import io.libp2p.core.PeerId;
import io.libp2p.etc.types.NothingToCompleteException;
import io.netty.handler.timeout.ReadTimeoutException;


public class LiteHost implements BitSwapNetwork {
    private static final String TAG = LiteHost.class.getSimpleName();
    @NonNull
    private final Host host;
    @NonNull
    private final ContentRouting contentRouting;


    private LiteHost(@NonNull Host host,
                     @NonNull ContentRouting contentRouting) {
        this.host = host;
        this.contentRouting = contentRouting;
    }

    public static BitSwapNetwork NewLiteHost(@NonNull Host host,
                                             @NonNull ContentRouting contentRouting) {
        return new LiteHost(host, contentRouting);
    }

    @Override
    public boolean ConnectTo(@NonNull Closeable closeable, @NonNull AddrInfo addrInfo, boolean protect) throws ClosedException {
        try {
            CompletableFuture<Connection> future = host.getNetwork().connect(
                    addrInfo.getPeerId(), addrInfo.getAddresses());
            // TODO closeable and timeout
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
                             @NonNull BitSwapMessage message, int timeout) throws ClosedException, ProtocolNotSupported, ConnectionFailure {

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

        } catch(ClosedException closedException){
            throw new ClosedException();
        } catch (Throwable throwable) {
            if (closeable.isClosed()) {
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
