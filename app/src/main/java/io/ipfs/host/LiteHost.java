package io.ipfs.host;

import androidx.annotation.NonNull;

import com.google.common.collect.Iterables;
import com.google.common.primitives.Bytes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.bitswap.BitSwapMessage;
import io.ipfs.bitswap.BitSwapNetwork;
import io.ipfs.bitswap.BitSwapProtocol;
import io.ipfs.cid.Cid;
import io.ipfs.core.AddrInfo;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.core.ConnectionIssue;
import io.ipfs.core.ProtocolIssue;
import io.ipfs.core.TimeoutIssue;
import io.ipfs.dht.KadDHT;
import io.ipfs.dht.Routing;
import io.ipfs.relay.Relay;
import io.ipns.Ipns;
import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionClosedException;
import io.libp2p.core.Host;
import io.libp2p.core.NoSuchRemoteProtocolException;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multiformats.Protocol;
import io.libp2p.core.transport.Transport;
import io.libp2p.etc.types.NonCompleteException;
import io.libp2p.etc.types.NothingToCompleteException;
import io.netty.handler.timeout.ReadTimeoutException;



public class LiteHost implements BitSwapNetwork {
    private static final String TAG = LiteHost.class.getSimpleName();
    private static final Duration DefaultRecordEOL = Duration.ofHours(24);
    @NonNull
    private final Host host;
    @NonNull
    private final Routing routing;
    @NonNull
    private final Metrics metrics;

    @NonNull
    private final Relay relay;

    public LiteHost(@NonNull Host host, @NonNull Metrics metrics, int alpha) {
        this.host = host;
        this.metrics = metrics;

        this.routing = new KadDHT(this,
                new Ipns(), alpha, IPFS.KAD_DHT_BETA,
                IPFS.KAD_DHT_BUCKET_SIZE);

        this.relay = new Relay(this);
    }

    @NonNull
    public Routing getRouting() {
        return routing;
    }

    @Override
    public boolean connectTo(@NonNull Closeable closeable, @NonNull PeerId peerId)
            throws ClosedException, ConnectionIssue {

        connect(closeable, peerId);
        return true; // TODO maybe not return anything, because an exception is thrown
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
    public PeerId Self() {
        return host.getPeerId();
    }

    @NonNull
    public List<Connection> getConnections() {
        return host.getNetwork().getConnections();
    }


    @Override
    public void writeMessage(@NonNull Closeable closeable, @NonNull PeerId peer,
                             @NonNull BitSwapMessage message)
            throws ClosedException, ProtocolIssue, TimeoutIssue, ConnectionIssue {


        try {
            synchronized (peer.toBase58().intern()) {
                Connection con = connect(closeable, peer);
                metrics.active(peer);
                Object object = stream(closeable, IPFS.ProtocolBitswap, con);

                BitSwapProtocol.BitSwapController controller = (BitSwapProtocol.BitSwapController) object;
                controller.sendRequest(message);
            }
        } catch (ClosedException | ConnectionIssue exception) {
            metrics.done(peer);
            throw exception;
        } catch (Throwable throwable) {
            metrics.done(peer);
            Throwable cause = throwable.getCause();
            if (cause != null) {
                if (cause instanceof NoSuchRemoteProtocolException) {
                    throw new ProtocolIssue();
                }
                if (cause instanceof NothingToCompleteException) {
                    throw new ConnectionIssue();
                }
                if (cause instanceof NonCompleteException) {
                    throw new ConnectionIssue();
                }
                if (cause instanceof ConnectionClosedException) {
                    throw new ConnectionIssue();
                }
                if (cause instanceof ReadTimeoutException) {
                    throw new TimeoutIssue();
                }
            }
            throw new RuntimeException(throwable);
        }

    }


    @Override
    public void findProviders(@NonNull Closeable closeable, @NonNull Routing.Providers providers,
                              @NonNull Cid cid) throws ClosedException {
        routing.FindProviders(closeable, providers, cid);
    }

    @NonNull
    public Connection connect(@NonNull Closeable closeable, @NonNull PeerId peerId)
            throws ConnectionIssue, ClosedException {

        try {
            if (closeable.isClosed()) {
                throw new ClosedException();
            }
            List<Multiaddr> addrInfo = prepareAddresses(peerId);

            if (!addrInfo.isEmpty()) {

                CompletableFuture<Connection> future = host.getNetwork().connect(peerId,
                        Iterables.toArray(addrInfo, Multiaddr.class));

                while (!future.isDone()) {
                    if (closeable.isClosed()) {
                        future.cancel(true);
                    }
                }
                if (closeable.isClosed()) {
                    throw new ClosedException();
                }

                return future.get();

            } else {
                return host.getNetwork().connect(peerId).get();
            }
        } catch (ClosedException closedException) {
            throw closedException;
        } catch (Throwable throwable) {
            throw new ConnectionIssue();
        }
    }

    @NonNull
    public Set<Multiaddr> getAddresses(@NonNull PeerId peerId) {
        Set<Multiaddr> all = new HashSet<>();
        try {
            Collection<Multiaddr> addrInfo = host.getAddressBook().get(peerId).get();
            if (addrInfo != null) {
                all.addAll(addrInfo);
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return all;
    }

    @NonNull
    private List<Multiaddr> prepareAddresses(@NonNull PeerId peerId) {
        List<Multiaddr> all = new ArrayList<>();
        for (Multiaddr ma : getAddresses(peerId)) {
            try {
                if (ma.has(Protocol.DNS6)) {
                    all.add(DnsResolver.resolveDns6(ma));
                } else if (ma.has(Protocol.DNS4)) {
                    all.add(DnsResolver.resolveDns4(ma));
                } else if (ma.has(Protocol.DNSADDR)) {
                    all.addAll(DnsResolver.resolveDnsAddress(ma));
                } else {
                    all.add(ma);
                }
            } catch (Throwable throwable) {
                LogUtils.verbose(TAG, throwable.getClass().getSimpleName());
            }
        }

        /*  todo seperate them by protocol QUIC when supported and rest
        all.sort((o1, o2) -> {

            // TODO better sorting
            int result = Boolean.compare(o1.has(Protocol.QUIC), o2.has(Protocol.QUIC));
            if (result == 0) {
                result = Boolean.compare(o1.has(Protocol.TCP), o2.has(Protocol.TCP));
            }
            return result;
        });*/
        return all;
    }


    @NonNull
    public Object stream(@NonNull Closeable closeable, @NonNull String protocol,
                         @NonNull Connection conn)
            throws InterruptedException, ExecutionException, ClosedException {


        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        CompletableFuture<Object> ctrl = host.newStream(
                Collections.singletonList(protocol), conn).getController();


        while (!ctrl.isDone()) {
            if (closeable.isClosed()) {
                ctrl.cancel(true);
            }
        }

        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        return ctrl.get();
    }

    public boolean canHop(@NonNull Closeable closeable, @NonNull PeerId peerId)
            throws ClosedException, ConnectionIssue {
        return relay.canHop(closeable, peerId);
    }


    public void PublishName(@NonNull Closeable closable,
                            @NonNull PrivKey privKey,
                            @NonNull String path,
                            @NonNull PeerId id, int sequence) throws ClosedException {


        Date eol = Date.from(new Date().toInstant().plus(DefaultRecordEOL));

        ipns.pb.Ipns.IpnsEntry
                record = Ipns.Create(privKey, path.getBytes(), sequence, eol);

        PubKey pk = privKey.publicKey();

        record = Ipns.EmbedPublicKey(pk, record);

        byte[] bytes = record.toByteArray();

        byte[] ipns = IPFS.IPNS_PATH.getBytes();
        byte[] ipnsKey = Bytes.concat(ipns, id.getBytes());
        routing.PutValue(closable, ipnsKey, bytes);
    }

    public boolean isConnected(@NonNull PeerId id) {
        try {
            return host.getNetwork().connect(id).get() != null;
        } catch (Throwable throwable) {
            return false;
        }
    }

    // TODO should be improved with information of the device real
    // public IP, probably asks other devices for getting
    // the real IP address (relay, punch-hole, etc stuff
    @NonNull
    public List<Multiaddr> listenAddresses() {
        try {
            // TODO the listen address does not contain real IP address

            List<Multiaddr> list = new ArrayList<>();
            List<Transport> transports = host.getNetwork().getTransports();
            for (Transport transport : transports) {
                list.addAll(transport.listenAddresses());
            }
            return list;
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return host.listenAddresses();

    }


    public void addAddrs(@NonNull AddrInfo addrInfo) {

        try {
            PeerId peerId = addrInfo.getPeerId();
            Collection<Multiaddr> info = host.getAddressBook().getAddrs(peerId).get();

            if (addrInfo.hasAddresses()) {
                if (info != null) {
                    host.getAddressBook().addAddrs(peerId, Long.MAX_VALUE, addrInfo.getAddresses());
                } else {
                    host.getAddressBook().setAddrs(peerId, Long.MAX_VALUE, addrInfo.getAddresses());
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public Metrics getMetrics() {
        return metrics;
    }
}
