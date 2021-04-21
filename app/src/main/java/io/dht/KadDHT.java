package io.dht;

import android.annotation.SuppressLint;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import dht.pb.Dht;
import io.LogUtils;
import io.core.Closeable;
import io.core.ClosedException;
import io.core.ConnectionFailure;
import io.core.ConnectionIssue;
import io.core.InvalidRecord;
import io.core.ProtocolIssue;
import io.core.Validator;
import io.ipfs.IPFS;
import io.ipfs.cid.Cid;
import io.libp2p.AddrInfo;
import io.libp2p.ConnectionManager;
import io.libp2p.HostBuilder;
import io.libp2p.Metrics;
import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionClosedException;
import io.libp2p.core.Host;
import io.libp2p.core.NoSuchRemoteProtocolException;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.etc.types.NonCompleteException;
import io.libp2p.etc.types.NothingToCompleteException;
import io.netty.handler.timeout.ReadTimeoutException;
import record.pb.RecordOuterClass;


public class KadDHT implements Routing {
    private static final String TAG = KadDHT.class.getSimpleName();
    public static final String Protocol = "/ipfs/kad/1.0.0";


    public final Host host;
    public final PeerId self;
    public final int beta;
    public final int bucketSize;
    public final int alpha;

    private final Validator validator;
    private final Metrics metrics;
    public final RoutingTable routingTable;

    public KadDHT(@NonNull Host host, @NonNull Metrics metrics,
                  @NonNull Validator validator, int alpha, int beta, int bucketSize) {
        this.host = host;
        this.metrics = metrics;
        this.validator = validator;
        this.self = host.getPeerId();
        ID selfKey = Util.ConvertPeerID(host.getPeerId());
        this.bucketSize = bucketSize;
        this.routingTable = new RoutingTable(metrics, bucketSize, selfKey);
        this.beta = beta;
        this.alpha = alpha;
    }

    public void init() {
        // Fill routing table with currently connected peers that are DHT servers

        for (Connection conn : host.getNetwork().getConnections()) {
            PeerId peerId = conn.secureSession().getRemoteId();
            metrics.addLatency(peerId, 0L);
            boolean isReplaceable = !metrics.isProtected(peerId);
            peerFound(peerId, isReplaceable);
        }
    }

    void peerFound(PeerId p, boolean isReplaceable) {
        try {
            routingTable.addPeer(p, isReplaceable);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }

    private void addAddrs(@NonNull AddrInfo addrInfo) {

        try {
            PeerId peerId = addrInfo.getPeerId();
            Collection<Multiaddr> info = host.getAddressBook().getAddrs(peerId).get();

            if (info != null) {
                host.getAddressBook().addAddrs(peerId, Long.MAX_VALUE, addrInfo.getAddresses());
            } else {
                host.getAddressBook().setAddrs(peerId, Long.MAX_VALUE, addrInfo.getAddresses());
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    @NonNull
    private List<AddrInfo> evalClosestPeers(@NonNull Dht.Message pms) {
        List<AddrInfo> peers = new ArrayList<>();
        List<Dht.Message.Peer> list = pms.getCloserPeersList();
        for (Dht.Message.Peer entry : list) {
            PeerId peerId = new PeerId(entry.getId().toByteArray());


            List<Multiaddr> multiAddresses = new ArrayList<>();
            List<ByteString> addresses = entry.getAddrsList();
            for (ByteString address : addresses) {
                Multiaddr multiaddr = preFilter(address);
                if (multiaddr != null) {
                    multiAddresses.add(multiaddr);
                }
            }
            AddrInfo addrInfo = AddrInfo.create(peerId, multiAddresses);
            if (addrInfo.hasAddresses()) {
                peers.add(addrInfo);
                addAddrs(addrInfo);
            }
        }
        return peers;
    }


    private void GetClosestPeers(@NonNull Closeable ctx, @NonNull byte[] key,
                                 @NonNull Channel channel) throws ClosedException {
        if (key.length == 0) {
            throw new RuntimeException("can't lookup empty key");
        }

        runLookupWithFollowup(ctx, key, (ctx1, p) -> {

            Dht.Message pms = findPeerSingle(ctx1, p, key);
            List<AddrInfo> peers = evalClosestPeers(pms);

            for (AddrInfo addrInfo : peers) {
                channel.peer(addrInfo);
            }

            return peers;
        }, ctx::isClosed);


    }

    @Override
    public void PutValue(@NonNull Closeable ctx, @NonNull byte[] key, @NonNull byte[] value) throws ClosedException {


        // don't allow local users to put bad values.
        try {
            validator.Validate(key, value);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }

        @SuppressLint("SimpleDateFormat") String format = new SimpleDateFormat(
                IPFS.TimeFormatIpfs).format(new Date());
        RecordOuterClass.Record rec = RecordOuterClass.Record.newBuilder().setKey(ByteString.copyFrom(key))
                .setValue(ByteString.copyFrom(value))
                .setTimeReceived(format).build();

        ConcurrentSkipListSet<PeerId> handled = new ConcurrentSkipListSet<>(
                (o1, o2) -> o1.toHex().compareTo(o2.toHex()));
        GetClosestPeers(ctx, key, addrInfo -> {
            PeerId peerId = addrInfo.getPeerId();
            if (!handled.contains(peerId)) {
                handled.add(peerId);
                try {
                    putValueToPeer(ctx, addrInfo.getPeerId(), rec);
                    LogUtils.error(TAG, "PutValue Success to " + addrInfo.getPeerId().toBase58());
                } catch (ClosedException closedException) {
                    throw closedException;
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, "PutValue Error " + throwable.getMessage());
                }
            }
        });

    }

    private void putValueToPeer(@NonNull Closeable ctx,
                                @NonNull PeerId p,
                                @NonNull RecordOuterClass.Record rec)
            throws ConnectionFailure, ProtocolIssue, ClosedException, ConnectionIssue {

        Dht.Message pms = Dht.Message.newBuilder()
                .setType(Dht.Message.MessageType.PUT_VALUE)
                .setKey(rec.getKey())
                .setRecord(rec)
                .setClusterLevelRaw(0).build();

        Dht.Message rimes = sendRequest(ctx, p, pms);

        if (!Arrays.equals(rimes.getRecord().getValue().toByteArray(),
                pms.getRecord().getValue().toByteArray())) {
            throw new RuntimeException("value not put correctly put-message  " +
                    pms.toString() + " get-message " + rimes.toString());
        }
    }

    @Override
    public void FindProviders(@NonNull Closeable closeable,
                              @NonNull Providers providers,
                              @NonNull Cid cid) throws ClosedException {
        if (!cid.Defined()) {
            throw new RuntimeException("Cid invalid");
        }

        byte[] key = cid.Hash();

        ConcurrentSkipListSet<PeerId> handled = new ConcurrentSkipListSet<>(
                (o1, o2) -> o1.toHex().compareTo(o2.toHex()));
        runLookupWithFollowup(closeable, key, (ctx, p) -> {

            Dht.Message pms = findProvidersSingle(ctx, p, key);

            List<AddrInfo> provs = new ArrayList<>();
            List<Dht.Message.Peer> list = pms.getProviderPeersList();
            for (Dht.Message.Peer entry : list) {

                PeerId peerId = new PeerId(entry.getId().toByteArray());
                LogUtils.error(TAG, "got provider before filter : " + peerId.toBase58());
                List<Multiaddr> multiAddresses = new ArrayList<>();
                List<ByteString> addresses = entry.getAddrsList();
                for (ByteString address : addresses) {
                    Multiaddr multiaddr = preFilter(address);
                    if (multiaddr != null) {
                        multiAddresses.add(multiaddr);
                    }
                }
                AddrInfo addrInfo = AddrInfo.create(peerId, multiAddresses);
                if (addrInfo.hasAddresses()) {
                    provs.add(addrInfo);
                    addAddrs(addrInfo);
                }
            }


            for (AddrInfo prov : provs) {
                LogUtils.error(TAG, "got provider : " + prov.getPeerId());
                PeerId peerId = prov.getPeerId();
                if (!handled.contains(peerId)) {
                    handled.add(peerId);
                    LogUtils.error(TAG, "got provider using: " + prov.getPeerId());
                    providers.peer(peerId);
                }
            }
            return evalClosestPeers(pms);

        }, closeable::isClosed);
    }


    public void removePeerFromDht(PeerId p) {
        routingTable.RemovePeer(p);
    }


    private Dht.Message makeProvRecord(@NonNull byte[] key) {

        List<Multiaddr> addresses = HostBuilder.listenAddresses(host);
        if (addresses.isEmpty()) {
            throw new RuntimeException("no known addresses for self, cannot put provider");
        }

        Dht.Message.Builder builder = Dht.Message.newBuilder()
                .setType(Dht.Message.MessageType.ADD_PROVIDER)
                .setKey(ByteString.copyFrom(key))
                .setClusterLevelRaw(0);

        Dht.Message.Peer.Builder peerBuilder = Dht.Message.Peer.newBuilder()
                .setId(ByteString.copyFrom(self.getBytes()));
        for (Multiaddr addr : addresses) {
            peerBuilder.addAddrs(ByteString.copyFrom(addr.getBytes()));
        }
        builder.addProviderPeers(peerBuilder.build());

        return builder.build();
    }

    @Override
    public void Provide(@NonNull Closeable ctx, @NonNull Cid cid) throws ClosedException {

        if (!cid.Defined()) {
            throw new RuntimeException("invalid cid: undefined");
        }

        byte[] key = cid.Hash();

        final Dht.Message mes = makeProvRecord(key);

        ConcurrentSkipListSet<PeerId> handled = new ConcurrentSkipListSet<>(
                (o1, o2) -> o1.toHex().compareTo(o2.toHex()));
        GetClosestPeers(ctx, key, addrInfo -> {
            PeerId peerId = addrInfo.getPeerId();
            if (!handled.contains(peerId)) {
                handled.add(peerId);
                try {
                    sendMessage(ctx, peerId, mes);
                    LogUtils.error(TAG, "Provide Success to " + addrInfo.getPeerId().toBase58());
                } catch (ClosedException closedException) {
                    throw closedException;
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, "Provide Error " + throwable.getClass().getName());
                }
            }
        });

    }


    private void sendMessage(@NonNull Closeable closeable, @NonNull PeerId p, @NonNull Dht.Message message)
            throws ClosedException, ConnectionIssue {


        Connection con = HostBuilder.connect(closeable, host, p);
        try {
            synchronized (p.toBase58().intern()) {
                long start = System.currentTimeMillis();
                metrics.active(p);
                Object object = HostBuilder.stream(closeable, host, KadDHT.Protocol, con);

                DhtProtocol.DhtController dhtController = (DhtProtocol.DhtController) object;
                dhtController.sendMessage(message);

                metrics.addLatency(p, System.currentTimeMillis() - start);
            }
        } catch (ClosedException exception) {
            throw exception;
        } catch (Throwable throwable) {
            if (closeable.isClosed()) {
                throw new ClosedException();
            }
            throw new RuntimeException(throwable);
        } finally {
            metrics.done(p);
        }
    }

    private Dht.Message sendRequest(@NonNull Closeable closeable, @NonNull PeerId p,
                                    @NonNull Dht.Message message)
            throws ClosedException, ProtocolIssue, ConnectionFailure, ConnectionIssue {


        Connection con = HostBuilder.connect(closeable, host, p);
        try {
            synchronized (p.toBase58().intern()) {
                long start = System.currentTimeMillis();

                Object object = HostBuilder.stream(closeable, host, KadDHT.Protocol, con);

                DhtProtocol.DhtController dhtController = (DhtProtocol.DhtController) object;
                Dht.Message response = dhtController.sendRequest(message).get();

                metrics.addLatency(p, System.currentTimeMillis() - start);

                return response;
            }
        } catch (ClosedException exception) {
            throw exception;
        } catch (Throwable throwable) {
            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            Throwable cause = throwable.getCause();
            if (cause != null) {
                LogUtils.error(TAG, cause.getClass().getSimpleName());
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
                    throw new ConnectionFailure();
                }
                if (cause instanceof ReadTimeoutException) {
                    throw new ConnectionFailure();
                }
            }
            LogUtils.error(TAG, throwable);
            throw new RuntimeException(throwable);
        } finally {
            metrics.done(p);
        }
    }


    private Dht.Message getValueSingle(@NonNull Closeable ctx, @NonNull PeerId p, @NonNull byte[] key)
            throws ConnectionFailure, ProtocolIssue, ClosedException, ConnectionIssue {
        Dht.Message pms = Dht.Message.newBuilder()
                .setType(Dht.Message.MessageType.GET_VALUE)
                .setKey(ByteString.copyFrom(key))
                .setClusterLevelRaw(0).build();
        return sendRequest(ctx, p, pms);
    }

    private Dht.Message findPeerSingle(@NonNull Closeable ctx, @NonNull PeerId p, @NonNull byte[] key)
            throws ClosedException, ProtocolIssue, ConnectionFailure, ConnectionIssue {
        Dht.Message pms = Dht.Message.newBuilder()
                .setType(Dht.Message.MessageType.FIND_NODE)
                .setKey(ByteString.copyFrom(key))
                .setClusterLevelRaw(0).build();

        return sendRequest(ctx, p, pms);
    }

    private Dht.Message findProvidersSingle(@NonNull Closeable ctx, @NonNull PeerId p, @NonNull byte[] key)
            throws ClosedException, ProtocolIssue, ConnectionFailure, ConnectionIssue {
        Dht.Message pms = Dht.Message.newBuilder()
                .setType(Dht.Message.MessageType.GET_PROVIDERS)
                .setKey(ByteString.copyFrom(key))
                .setClusterLevelRaw(0).build();
        return sendRequest(ctx, p, pms);
    }

    @Nullable
    private AddrInfo FindLocal(@NonNull PeerId id) {
        List<Connection> cons = host.getNetwork().getConnections();
        for (Connection con : cons) {
            if (Objects.equals(con.secureSession().getRemoteId(), id)) {
                return AddrInfo.create(id, con.remoteAddress());
            }
        }
        return null;
    }

    @Nullable
    private Multiaddr preFilter(@NonNull ByteString address) {
        try {
            return new Multiaddr(address.toByteArray());
        } catch (Throwable ignore) {
            LogUtils.error(TAG, address.toStringUtf8());
        }
        return null;
    }

    @Override
    public boolean FindPeer(@NonNull Closeable closeable, @NonNull PeerId id) throws ClosedException {

        AddrInfo pi = FindLocal(id);
        if (pi != null) {
            return true;
        }

        byte[] key = id.getBytes();

        LookupWithFollowupResult lookupRes = runLookupWithFollowup(closeable, key,
                (ctx, p) -> {
                    Dht.Message pms = findPeerSingle(ctx, p, id.getBytes());
                    return evalClosestPeers(pms);
                }, () -> closeable.isClosed() || HostBuilder.isConnected(host, id));


        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        boolean dialedPeerDuringQuery = false;
        Objects.requireNonNull(lookupRes);
        PeerState state = lookupRes.peers.get(id);
        if (state != null) {

            // Note: we consider PeerUnreachable to be a valid state because the peer may not support the DHT protocol
            // and therefore the peer would fail the query. The fact that a peer that is returned can be a non-DHT
            // server peer and is not identified as such is a bug.
            dialedPeerDuringQuery = (state == PeerState.PeerQueried ||
                    state == PeerState.PeerUnreachable || state == PeerState.PeerWaiting);

        }

        boolean connectedness = HostBuilder.isConnected(host, id);
        return dialedPeerDuringQuery || connectedness;
    }

    private LookupWithFollowupResult runQuery(@NonNull Closeable ctx, @NonNull byte[] target,
                                              @NonNull QueryFunc queryFn, @NonNull StopFunc stopFn)
            throws ClosedException, InterruptedException {
        // pick the K closest peers to the key in our Routing table.
        ID targetKadID = Util.ConvertKey(target);
        List<PeerId> seedPeers = routingTable.NearestPeers(targetKadID, bucketSize);
        if (seedPeers.size() == 0) {
            throw new ClosedException();
        }

        Query q = new Query(this, target, seedPeers, queryFn, stopFn);

        q.run(ctx);

        return q.constructLookupResult(targetKadID);
    }

    // runLookupWithFollowup executes the lookup on the target using the given query function and stopping when either the
    // context is cancelled or the stop function returns true. Note: if the stop function is not sticky, i.e. it does not
    // return true every time after the first time it returns true, it is not guaranteed to cause a stop to occur just
    // because it momentarily returns true.
    //
    // After the lookup is complete the query function is run (unless stopped) against all of the top K peers from the
    // lookup that have not already been successfully queried.
    private LookupWithFollowupResult runLookupWithFollowup(@NonNull Closeable ctx, @NonNull byte[] target,
                                                           @NonNull QueryFunc queryFn, @NonNull StopFunc stopFn)
            throws ClosedException {

        try {
            LookupWithFollowupResult lookupRes = runQuery(ctx, target, queryFn, stopFn);


            // query all of the top K peers we've either Heard about or have outstanding queries we're Waiting on.
            // This ensures that all of the top K results have been queried which adds to resiliency against churn for query
            // functions that carry state (e.g. FindProviders and GetValue) as well as establish connections that are needed
            // by stateless query functions (e.g. GetClosestPeers and therefore Provide and PutValue)

            List<PeerId> queryPeers = new ArrayList<>();
            for (Map.Entry<PeerId, PeerState> entry : lookupRes.peers.entrySet()) {
                PeerState state = entry.getValue();
                if (state == PeerState.PeerHeard || state == PeerState.PeerWaiting) {
                    queryPeers.add(entry.getKey());
                }
            }

            if (queryPeers.size() == 0) {
                return lookupRes;
            }

            if (stopFn.stop()) {
                lookupRes.completed = false;
                return lookupRes;
            }

            List<Callable<List<AddrInfo>>> tasks = new ArrayList<>();
            for (PeerId p : queryPeers) {
                tasks.add(() -> queryFn.query(ctx, p));
            }
            ExecutorService executor = Executors.newFixedThreadPool(4);
            int followupsCompleted = 0;
            List<Future<List<AddrInfo>>> futures = executor.invokeAll(tasks);
            for (Future<List<AddrInfo>> future : futures) {
                try {
                    future.get();
                    followupsCompleted++;
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable.getClass().getSimpleName());
                }
            }
            if (!lookupRes.completed) {
                lookupRes.completed = followupsCompleted == queryPeers.size();
            }

            return lookupRes;
        } catch (InterruptedException ignore) {
            throw new ClosedException();
        }

    }


    private Pair<RecordOuterClass.Record, List<AddrInfo>> getValueOrPeers(
            @NonNull Closeable ctx, @NonNull PeerId p, @NonNull byte[] key)
            throws ConnectionFailure, ClosedException, ProtocolIssue, ConnectionIssue {


        Dht.Message pms = getValueSingle(ctx, p, key);
        List<AddrInfo> peers = evalClosestPeers(pms);

        if (pms.hasRecord()) {
            RecordOuterClass.Record rec = pms.getRecord();
            try {
                byte[] record = rec.getValue().toByteArray();
                if (record != null && record.length > 0) {
                    validator.Validate(rec.getKey().toByteArray(), record);
                    return Pair.create(rec, peers);
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, "" + throwable.getMessage());
            }
        }

        if (peers.size() > 0) {
            return Pair.create(null, peers);
        }
        return Pair.create(null, Collections.emptyList());
    }

    private void getValues(@NonNull Closeable ctx, @NonNull RecordValueFunc recordFunc,
                           @NonNull byte[] key, @NonNull StopFunc stopQuery) throws ClosedException {


        runLookupWithFollowup(ctx, key, (ctx1, p) -> {

            Pair<RecordOuterClass.Record, List<AddrInfo>> result = getValueOrPeers(ctx1, p, key);
            RecordOuterClass.Record rec = result.first;
            List<AddrInfo> peers = result.second;

            if (rec != null) {
                recordFunc.record(new RecordInfo(rec.getValue().toByteArray()));
            }

            return peers;
        }, stopQuery);

    }


    private void processValues(@NonNull Closeable ctx, @Nullable RecordInfo best,
                               @NonNull RecordInfo v, @NonNull RecordReportFunc reporter) {

        if (best != null) {
            if (Arrays.equals(best.Val, v.Val)) {
                reporter.report(ctx, v, false);
            } else {
                int value = validator.Select(best.Val, v.Val);

                if (value == -1) {
                    reporter.report(ctx, v, false);
                }
            }
        } else {
            reporter.report(ctx, v, true);
        }
    }


    @Override
    public void SearchValue(@NonNull Closeable ctx, @NonNull ResolveInfo resolveInfo,
                            @NonNull byte[] key, final int quorum) throws ClosedException {

        AtomicInteger numResponses = new AtomicInteger(0);
        AtomicReference<RecordInfo> best = new AtomicReference<>();
        getValues(ctx, recordVal -> processValues(ctx, best.get(), recordVal, (ctx1, v, better) -> {
            numResponses.incrementAndGet();
            if (better) {
                resolveInfo.resolved(v.Val);
                best.set(v);
            }
        }), key, () -> numResponses.get() == quorum);

    }


    public interface StopFunc {
        boolean stop();
    }

    public interface QueryFunc {
        @NonNull
        List<AddrInfo> query(@NonNull Closeable ctx, @NonNull PeerId peerId)
                throws ClosedException, ProtocolIssue, ConnectionFailure, InvalidRecord, ConnectionIssue;
    }


    public interface RecordValueFunc {
        void record(@NonNull RecordInfo recordInfo);
    }

    public interface RecordReportFunc {
        void report(@NonNull Closeable ctx, @NonNull RecordInfo v, boolean better);
    }


    private static class RecordInfo {
        byte[] Val;

        public RecordInfo(@NonNull byte[] data) {
            this.Val = data;
        }
    }

    public interface Channel {
        void peer(@NonNull AddrInfo addrInfo) throws ClosedException;
    }
}
