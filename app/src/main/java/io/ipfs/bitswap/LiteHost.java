package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import com.google.common.collect.Iterables;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.LogUtils;
import io.core.Closeable;
import io.core.ClosedException;
import io.core.ConnectionFailure;
import io.core.ProtocolNotSupported;
import io.dht.Channel;
import io.dht.Routing;
import io.ipfs.IPFS;
import io.ipfs.cid.Cid;
import io.libp2p.AddrInfo;
import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionClosedException;
import io.libp2p.core.Host;
import io.libp2p.core.NoSuchRemoteProtocolException;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.etc.types.NothingToCompleteException;
import io.netty.handler.timeout.ReadTimeoutException;


public class LiteHost implements BitSwapNetwork {
    private static final String TAG = LiteHost.class.getSimpleName();
    @NonNull
    private final Host host;
    @NonNull
    private final Routing routing;


    private LiteHost(@NonNull Host host, @NonNull Routing routing) {
        this.host = host;
        this.routing = routing;
    }

    public static BitSwapNetwork NewLiteHost(@NonNull Host host, @NonNull Routing routing) {
        return new LiteHost(host, routing);
    }

    @Override
    public boolean ConnectTo(@NonNull Closeable closeable, @NonNull AddrInfo addrInfo, boolean protect) throws ClosedException {
        try {
            CompletableFuture<Connection> future = host.getNetwork().connect(
                    addrInfo.getPeerId(), addrInfo.getAddresses());
            // TODO closeable and timeout
            return future.get() != null;
        } catch (Throwable throwable) {
            if (closeable.isClosed()) {
                throw new ClosedException();
            }
            return false;
        }

    }


    @NonNull
    @Override
    public Set<PeerId> getPeers() {
        Set<PeerId> peerIds = new HashSet<>();
        for (Connection connection : host.getNetwork().getConnections()) {
            peerIds.add(connection.secureSession().getRemoteId());
        }

        return peerIds;
    }


    @Override
    public void WriteMessage(@NonNull Closeable closeable, @NonNull PeerId peer,
                             @NonNull BitSwapMessage message)
            throws ClosedException, ProtocolNotSupported, ConnectionFailure {

        synchronized (peer.toBase58().intern()) { // TODO rethink
            try {

                if (closeable.isClosed()) {
                    throw new ClosedException();
                }
                Multiaddr[] addrs = null;
                Collection<Multiaddr> addrInfo = host.getAddressBook().get(peer).get();
                if (addrInfo != null) {
                    addrs = Iterables.toArray(addrInfo, Multiaddr.class);
                }
                if(addrs != null) {
                    host.getNetwork().connect(peer, addrs).get();
                } else{
                    host.getNetwork().connect(peer).get();
                }


                if (closeable.isClosed()) {
                    throw new ClosedException();
                }

                CompletableFuture<Object> ctrl = host.newStream(
                        Collections.singletonList(IPFS.ProtocolBitswap), peer).getController();
                Object object = ctrl.get(IPFS.WRITE_TIMEOUT, TimeUnit.SECONDS); // TODO timeout
                //Object object = ctrl.get();

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

            } catch (ClosedException closedException) {
                throw new ClosedException();
            } catch (Throwable throwable) {
                if (closeable.isClosed()) {
                    throw new ClosedException();
                }
                if(throwable instanceof TimeoutException){
                    LogUtils.error(TAG, "Timeout excepiton");
                    throw new ConnectionFailure();
                }
                Throwable cause = throwable.getCause();
                if(cause != null) {
                    LogUtils.error(TAG, cause.getClass().getSimpleName());
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
                }
                throw new RuntimeException(throwable);
            }
        }
    }


    @Override
    public void FindProvidersAsync(@NonNull Closeable closeable, @NonNull Channel channel,
                                   @NonNull Cid cid) throws ClosedException {
        LogUtils.error(TAG, "Find Start Content Provider " + cid.String());
        routing.FindProviders(closeable, channel, cid);
        LogUtils.error(TAG, "Find End Content Provider " + cid.String());

    }
}
