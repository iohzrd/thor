package io.ipfs;

import androidx.annotation.NonNull;

import com.google.common.primitives.Bytes;

import java.time.Duration;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import io.core.Closeable;
import io.core.ClosedException;
import io.core.ConnectionIssue;
import io.core.ProtocolIssue;
import io.core.TimeoutIssue;
import io.dht.KadDHT;
import io.dht.Routing;
import io.ipfs.bitswap.BitSwapMessage;
import io.ipfs.bitswap.BitSwapNetwork;
import io.ipfs.bitswap.BitSwapProtocol;
import io.ipfs.cid.Cid;
import io.ipfs.relay.Relay;
import io.ipns.Ipns;
import io.libp2p.HostBuilder;
import io.libp2p.Metrics;
import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionClosedException;
import io.libp2p.core.Host;
import io.libp2p.core.NoSuchRemoteProtocolException;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.etc.types.NonCompleteException;
import io.libp2p.etc.types.NothingToCompleteException;
import io.netty.handler.timeout.ReadTimeoutException;
import io.protos.ipns.IpnsProtos;


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

    LiteHost(@NonNull Host host, @NonNull Metrics metrics, int alpha) {
        this.host = host;
        this.metrics = metrics;

        this.routing = new KadDHT(host, metrics,
                new Ipns(), alpha, IPFS.KAD_DHT_BETA,
                IPFS.KAD_DHT_BUCKET_SIZE);

        this.relay = new Relay(this);
    }

    @NonNull
    public Routing getRouting() {
        return routing;
    }

    @Override
    public boolean ConnectTo(@NonNull Closeable closeable, @NonNull PeerId peerId)
            throws ClosedException, ConnectionIssue {

        return HostBuilder.connect(closeable, host, peerId) != null;
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


    @Override
    public void WriteMessage(@NonNull Closeable closeable, @NonNull PeerId peer,
                             @NonNull BitSwapMessage message)
            throws ClosedException, ProtocolIssue, TimeoutIssue, ConnectionIssue {

        Connection con = HostBuilder.connect(closeable, host, peer);
        try {
            synchronized (peer.toBase58().intern()) {

                if (closeable.isClosed()) {
                    throw new ClosedException();
                }

                metrics.active(peer);
                Object object = stream(closeable, IPFS.ProtocolBitswap, con);

                BitSwapProtocol.BitSwapController controller = (BitSwapProtocol.BitSwapController) object;
                controller.sendRequest(message);
            }
        } catch (ClosedException exception) {
            throw exception;
        } catch (Throwable throwable) {

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
        } finally {
            metrics.done(peer);
        }

    }


    @Override
    public void FindProviders(@NonNull Closeable closeable, @NonNull Routing.Providers providers,
                              @NonNull Cid cid) throws ClosedException {
        routing.FindProviders(closeable, providers, cid);
    }

    @NonNull
    public Connection connect(@NonNull Closeable closeable, @NonNull PeerId peerId)
            throws ConnectionIssue, ClosedException {
        return HostBuilder.connect(closeable, host, peerId);
    }

    @NonNull
    public Object stream(@NonNull Closeable closeable, @NonNull String protocol,
                         @NonNull Connection conn)
            throws InterruptedException, ExecutionException, ClosedException {
        return HostBuilder.stream(closeable, host, protocol, conn);
    }

    public boolean canHop(@NonNull Closeable closeable, @NonNull PeerId peerId)
            throws ClosedException, ConnectionIssue {
        return relay.canHop(closeable, peerId);
    }


    public void PublishName(@NonNull Closeable closable,
                            @NonNull PrivKey privKey, @NonNull String path,
                            @NonNull PeerId id, int sequence) throws ClosedException {


        Date eol = Date.from(new Date().toInstant().plus(DefaultRecordEOL));

        IpnsProtos.IpnsEntry
                record = Ipns.Create(privKey, path.getBytes(), sequence, eol);

        PubKey pk = privKey.publicKey();

        record = Ipns.EmbedPublicKey(pk, record);

        byte[] bytes = record.toByteArray();

        byte[] ipns = IPFS.IPNS_PATH.getBytes();
        byte[] ipnsKey = Bytes.concat(ipns, id.getBytes());
        routing.PutValue(closable, ipnsKey, bytes);
    }
}
