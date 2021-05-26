package io.ipfs.dht;

import android.annotation.SuppressLint;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import dht.pb.Dht;
import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.core.ConnectionIssue;
import io.ipfs.core.ProtocolIssue;
import io.ipfs.core.RecordIssue;
import io.ipfs.core.TimeoutIssue;
import io.ipfs.core.Validator;
import io.ipfs.host.AddrInfo;
import io.ipfs.host.Connection;
import io.ipfs.host.DnsResolver;
import io.ipfs.host.LiteHost;
import io.ipfs.host.PeerId;
import io.ipfs.ipns.Ipns;
import io.ipfs.multiaddr.Multiaddr;
import io.ipfs.multiaddr.Protocol;
import io.ipfs.utils.DataHandler;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamPriority;
import io.netty.incubator.codec.quic.QuicStreamType;
import record.pb.RecordOuterClass;


public class KadDht implements Routing {

    private static final String TAG = KadDht.class.getSimpleName();
    public final LiteHost host;
    public final PeerId self;
    public final int beta;
    public final int bucketSize;
    public final int alpha;
    public final RoutingTable routingTable;
    private final Validator validator;


    public KadDht(@NonNull LiteHost host, @NonNull Validator validator,
                  int alpha, int beta, int bucketSize) {
        this.host = host;
        this.validator = validator;
        this.self = host.self();
        this.bucketSize = bucketSize;
        this.routingTable = new RoutingTable(bucketSize, Util.ConvertPeerID(self));
        this.beta = beta;
        this.alpha = alpha;

    }

    @Override
    public void bootstrap() {
        // Fill routing table with currently connected peers that are DHT servers
        synchronized (TAG.intern()) {
            try {
                Set<String> addresses = new HashSet<>(IPFS.DHT_BOOTSTRAP_NODES);

                for (String multiAddress : addresses) {
                    try {
                        Multiaddr multiaddr = new Multiaddr(multiAddress);
                        String name = multiaddr.getStringComponent(Protocol.Type.P2P);
                        Objects.requireNonNull(name);
                        PeerId peerId = PeerId.fromBase58(name);
                        Objects.requireNonNull(peerId);

                        List<Multiaddr> result = DnsResolver.resolveDnsAddress(multiaddr);

                        AddrInfo addrInfo = AddrInfo.create(peerId, result, false);

                        if (addrInfo.hasAddresses()) {
                            host.protectPeer(peerId, host.getMaxTime());
                            host.addToAddressBook(addrInfo);
                            peerFound(peerId, false);
                        }
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }
    }

    void peerFound(PeerId p, boolean isReplaceable) {
        try {
            routingTable.addPeer(p, isReplaceable);
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

            AddrInfo addrInfo = AddrInfo.create(peerId, multiAddresses, false);
            if (addrInfo.hasAddresses()) {
                peers.add(addrInfo);
                host.addToAddressBook(addrInfo);
            } else {
                LogUtils.debug(TAG, "Ignore evalClosestPeers : " +
                        peerId.toBase58() + " " + multiAddresses.toString());
            }
        }
        return peers;
    }


    private void getClosestPeers(@NonNull Closeable closeable, @NonNull byte[] key,
                                 @NonNull Channel channel) throws ClosedException {
        if (key.length == 0) {
            throw new RuntimeException("can't lookup empty key");
        }

        runLookupWithFollowup(closeable, key, (ctx1, p) -> {

            Dht.Message pms = findPeerSingle(ctx1, p, key);

            List<AddrInfo> peers = evalClosestPeers(pms);

            for (AddrInfo addrInfo : peers) {
                channel.peer(addrInfo);
            }

            return peers;
        }, closeable::isClosed);


    }

    @Override
    public void putValue(@NonNull Closeable ctx, @NonNull byte[] key, @NonNull byte[] value) {


        // don't allow local users to put bad values.
        try {
            Ipns.Entry entry = validator.validate(key, value);
            Objects.requireNonNull(entry);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }

        long start = System.currentTimeMillis();

        @SuppressLint("SimpleDateFormat") String format = new SimpleDateFormat(
                IPFS.TimeFormatIpfs).format(new Date());
        RecordOuterClass.Record rec = RecordOuterClass.Record.newBuilder().setKey(ByteString.copyFrom(key))
                .setValue(ByteString.copyFrom(value))
                .setTimeReceived(format).build();

        ConcurrentSkipListSet<PeerId> handled = new ConcurrentSkipListSet<>();
        try {
            getClosestPeers(ctx, key, addrInfo -> {
                PeerId peerId = addrInfo.getPeerId();
                if (!handled.contains(peerId)) {
                    handled.add(peerId);
                    ExecutorService service = Executors.newSingleThreadExecutor();
                    service.execute(() -> putValueToPeer(ctx, addrInfo.getPeerId(), rec));
                }
            });
        } catch (ClosedException ignore) {
        } finally {
            LogUtils.verbose(TAG, "Finish putValue at " + (System.currentTimeMillis() - start));
        }

    }

    private void putValueToPeer(@NonNull Closeable ctx, @NonNull PeerId p,
                                @NonNull RecordOuterClass.Record rec) {

        try {
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
            LogUtils.verbose(TAG, "PutValue Success to " + p.toBase58());
        } catch (ClosedException | ConnectionIssue | TimeoutIssue ignore) {
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }

    @Override
    public void findProviders(@NonNull Closeable closeable, @NonNull Providers providers,
                              @NonNull Cid cid) throws ClosedException {
        if (!cid.Defined()) {
            throw new RuntimeException("Cid invalid");
        }

        byte[] key = cid.Hash();

        long start = System.currentTimeMillis();

        try {
            ConcurrentSkipListSet<PeerId> handled = new ConcurrentSkipListSet<>();
            runLookupWithFollowup(closeable, key, (ctx, p) -> {

                Dht.Message pms = findProvidersSingle(ctx, p, key);

                List<AddrInfo> provs = new ArrayList<>();
                List<Dht.Message.Peer> list = pms.getProviderPeersList();
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

                    LogUtils.debug(TAG, "got provider before filter " + peerId.toBase58() + " "
                            + multiAddresses.toString());

                    AddrInfo addrInfo = AddrInfo.create(peerId, multiAddresses, false);

                    // maybe in the future empty addresses will also reported
                    // but an adequate searching should be provided
                    if (addrInfo.hasAddresses()) {
                        provs.add(addrInfo);
                        host.addToAddressBook(addrInfo);
                    }
                }


                for (AddrInfo prov : provs) {
                    LogUtils.debug(TAG, "got provider : " + prov.getPeerId());
                    PeerId peerId = prov.getPeerId();
                    if (!handled.contains(peerId)) {
                        handled.add(peerId);
                        LogUtils.info(TAG, "got provider using: " + prov.getPeerId());
                        providers.peer(peerId);
                    }
                }
                return evalClosestPeers(pms);

            }, closeable::isClosed);
        } finally {
            LogUtils.debug(TAG, "Finished findProviders at " +
                    (System.currentTimeMillis() - start));
        }
    }


    public void removeFromRouting(PeerId p) {
        boolean result = routingTable.removePeer(p);
        if (result) {
            LogUtils.debug(TAG, "Remove from routing " + p.toBase58());
        }
    }


    private Dht.Message makeProvRecord(@NonNull byte[] key) {

        List<Multiaddr> addresses = host.listenAddresses();
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
    public void provide(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException {

        if (!cid.Defined()) {
            throw new RuntimeException("invalid cid: undefined");
        }

        byte[] key = cid.Hash();

        final Dht.Message mes = makeProvRecord(key);

        ConcurrentSkipListSet<PeerId> handled = new ConcurrentSkipListSet<>();
        getClosestPeers(closeable, key, addrInfo -> {
            PeerId peerId = addrInfo.getPeerId();
            if (!handled.contains(peerId)) {
                handled.add(peerId);
                ExecutorService service = Executors.newSingleThreadExecutor();
                service.execute(() -> sendMessage(closeable, peerId, mes));
            }
        });

    }


    private void sendMessage(@NonNull Closeable closeable, @NonNull PeerId p,
                             @NonNull Dht.Message message) {
        long time = System.currentTimeMillis();
        Connection conn = null;
        try {
            conn = host.connect(closeable, p, IPFS.CONNECT_TIMEOUT);

            if (closeable.isClosed()) {
                throw new ClosedException();
            }
            QuicChannel quicChannel = conn.channel();

            CompletableFuture<Void> stream = new CompletableFuture<>();
            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new KadDhtSend(stream, message)).sync().get();

            streamChannel.updatePriority(new QuicStreamPriority(IPFS.PRIORITY_HIGH, false));
            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.DHT_PROTOCOL));

            stream.get(IPFS.CONNECT_TIMEOUT, TimeUnit.SECONDS);
            streamChannel.close().get();

        } catch (ClosedException | ConnectionIssue | TimeoutException ignore) {
            // ignore
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        } finally {
            LogUtils.debug(TAG, "Send took " + (System.currentTimeMillis() - time));
            if (conn != null) {
                conn.disconnect();
            }
        }
    }


    private Dht.Message sendRequest(@NonNull Closeable closeable, @NonNull PeerId p,
                                    @NonNull Dht.Message message)
            throws ClosedException, ProtocolIssue, TimeoutIssue, ConnectionIssue {

        long time = System.currentTimeMillis();
        boolean success = false;

        Connection conn = null;

        try {
            conn = host.connect(closeable, p, IPFS.CONNECT_TIMEOUT);

            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            time = System.currentTimeMillis();

            QuicChannel quicChannel = conn.channel();

            CompletableFuture<Dht.Message> request = request(quicChannel,
                    message, IPFS.PRIORITY_NORMAL);

                /*
                while (!request.isDone()) {
                    if (closeable.isClosed()) {
                        request.cancel(true);
                    }
                }

                if (closeable.isClosed()) {
                    throw new ClosedException();
                }*/

            Dht.Message msg = request.get(IPFS.DHT_REQUEST_READ_TIMEOUT, TimeUnit.SECONDS);
            Objects.requireNonNull(msg);
            success = true;
            p.setLatency(System.currentTimeMillis() - time);

            return msg;

        } catch (ClosedException | ConnectionIssue exception) {
            throw exception;
        } catch (TimeoutException exception) {
            throw new TimeoutIssue();
        } catch (Throwable throwable) {
            Throwable cause = throwable.getCause();
            if (cause != null) {
                LogUtils.info(TAG, cause.getClass().getSimpleName());
                if (cause instanceof ProtocolIssue) {
                    throw new ProtocolIssue();
                }
                if (cause instanceof ConnectionIssue) {
                    throw new ConnectionIssue();
                }
                if (cause instanceof TimeoutException) {
                    throw new TimeoutIssue();
                }
                if (cause instanceof ReadTimeoutException) {
                    throw new TimeoutIssue();
                }
            }
            LogUtils.error(TAG, throwable);
            throw new ConnectionIssue();
        } finally {
            LogUtils.debug(TAG, "Request " + success + " took " +
                    (System.currentTimeMillis() - time));
            if (conn != null) {
                conn.disconnect();
            }
        }

    }

    public CompletableFuture<Dht.Message> request(@NonNull QuicChannel quicChannel,
                                                  @NonNull MessageLite messageLite,
                                                  short priority) {

        CompletableFuture<Dht.Message> request = new CompletableFuture<>();
        CompletableFuture<Void> activation = new CompletableFuture<>();

        try {
            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new KadDhtRequest(activation, request, messageLite)).sync().get();


            streamChannel.pipeline().addFirst(new ReadTimeoutHandler(
                    IPFS.DHT_REQUEST_READ_TIMEOUT, TimeUnit.SECONDS));

            streamChannel.updatePriority(new QuicStreamPriority(priority, false));


            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.DHT_PROTOCOL));

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            activation.completeExceptionally(throwable);
            request.completeExceptionally(throwable);
        }

        return activation.thenCompose(s -> request);
    }


    private Dht.Message getValueSingle(@NonNull Closeable ctx, @NonNull PeerId p, @NonNull byte[] key)
            throws TimeoutIssue, ProtocolIssue, ClosedException, ConnectionIssue {
        Dht.Message pms = Dht.Message.newBuilder()
                .setType(Dht.Message.MessageType.GET_VALUE)
                .setKey(ByteString.copyFrom(key))
                .setClusterLevelRaw(0).build();
        return sendRequest(ctx, p, pms);
    }

    private Dht.Message findPeerSingle(@NonNull Closeable ctx, @NonNull PeerId p, @NonNull byte[] key)
            throws ClosedException, ProtocolIssue, TimeoutIssue, ConnectionIssue {
        Dht.Message pms = Dht.Message.newBuilder()
                .setType(Dht.Message.MessageType.FIND_NODE)
                .setKey(ByteString.copyFrom(key))
                .setClusterLevelRaw(0).build();

        return sendRequest(ctx, p, pms);
    }

    private Dht.Message findProvidersSingle(@NonNull Closeable ctx, @NonNull PeerId p, @NonNull byte[] key)
            throws ClosedException, ProtocolIssue, TimeoutIssue, ConnectionIssue {
        Dht.Message pms = Dht.Message.newBuilder()
                .setType(Dht.Message.MessageType.GET_PROVIDERS)
                .setKey(ByteString.copyFrom(key))
                .setClusterLevelRaw(0).build();
        return sendRequest(ctx, p, pms);
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
    public void findPeer(@NonNull Closeable closeable, @NonNull Updater updater, @NonNull PeerId id) {


        byte[] key = id.getBytes();
        long start = System.currentTimeMillis();
        try {
            runLookupWithFollowup(closeable, key, (ctx, p) -> {
                Dht.Message pms = findPeerSingle(ctx, p, id.getBytes());

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

                    AddrInfo addrInfo = AddrInfo.create(peerId, multiAddresses, false);
                    if (addrInfo.hasAddresses()) {
                        peers.add(addrInfo);
                        boolean update = host.addToAddressBook(addrInfo);
                        if (update) {
                            if (Objects.equals(peerId, id)) {
                                LogUtils.debug(TAG, "findPeer " + addrInfo.toString());
                                updater.peer(peerId);
                            }
                        }
                    } else {
                        LogUtils.debug(TAG, "Ignore evalClosestPeers : " +
                                peerId.toBase58() + " " + multiAddresses.toString());
                    }
                }

                if (ctx.isClosed()) {
                    return Collections.emptyList();
                }
                return peers;

            }, closeable::isClosed);
        } catch (Throwable ignore) {
        } finally {
            LogUtils.verbose(TAG, "Finish findPeer at " + (System.currentTimeMillis() - start));
        }
    }

    private ConcurrentHashMap<PeerId, PeerState> runQuery(@NonNull Closeable ctx, @NonNull byte[] target,
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
    private void runLookupWithFollowup(@NonNull Closeable ctx, @NonNull byte[] target,
                                       @NonNull QueryFunc queryFn, @NonNull StopFunc stopFn)
            throws ClosedException {

        try {

            if (ctx.isClosed()) {
                return;
            }

            ConcurrentHashMap<PeerId, PeerState> lookupRes = runQuery(ctx, target, queryFn, stopFn);


            // query all of the top K peers we've either Heard about or have outstanding queries we're Waiting on.
            // This ensures that all of the top K results have been queried which adds to resiliency against churn for query
            // functions that carry state (e.g. FindProviders and GetValue) as well as establish connections that are needed
            // by stateless query functions (e.g. GetClosestPeers and therefore Provide and PutValue)

            List<PeerId> queryPeers = new ArrayList<>();
            for (Map.Entry<PeerId, PeerState> entry : lookupRes.entrySet()) {
                PeerState state = entry.getValue();
                if (state == PeerState.PeerHeard || state == PeerState.PeerWaiting) {
                    queryPeers.add(entry.getKey());
                }
            }

            if (queryPeers.size() == 0) {
                return;
            }

            if (stopFn.stop()) {
                return;
            }

            // TODO still check if necessary
            ExecutorService executor = Executors.newFixedThreadPool(4);
            for (PeerId p : queryPeers) {

                executor.execute(() -> {
                    if (ctx.isClosed()) {
                        return;
                    }

                    try {
                        queryFn.query(ctx, p);
                    } catch (Throwable ignore) {
                        // ignore
                    }

                });
            }

        } catch (InterruptedException ignore) {
            throw new ClosedException();
        }

    }


    private Pair<Ipns.Entry, List<AddrInfo>> getValueOrPeers(
            @NonNull Closeable ctx, @NonNull PeerId p, @NonNull byte[] key)
            throws TimeoutIssue, ClosedException, ProtocolIssue, ConnectionIssue {


        Dht.Message pms = getValueSingle(ctx, p, key);

        List<AddrInfo> peers = evalClosestPeers(pms);

        if (pms.hasRecord()) {

            RecordOuterClass.Record rec = pms.getRecord();
            try {
                byte[] record = rec.getValue().toByteArray();
                if (record != null && record.length > 0) {
                    Ipns.Entry entry = validator.validate(rec.getKey().toByteArray(), record);
                    return Pair.create(entry, peers);
                }
            } catch (RecordIssue issue) {
                LogUtils.debug(TAG, issue.getMessage());
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
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

            Pair<Ipns.Entry, List<AddrInfo>> result = getValueOrPeers(ctx1, p, key);
            Ipns.Entry entry = result.first;
            List<AddrInfo> peers = result.second;

            if (entry != null) {
                recordFunc.record(entry);
            }

            return peers;
        }, stopQuery);

    }


    private void processValues(@NonNull Closeable ctx, @Nullable Ipns.Entry best,
                               @NonNull Ipns.Entry v, @NonNull RecordReportFunc reporter) {

        if (best != null) {
            if (Objects.equals(best, v)) {
                reporter.report(ctx, v, false);
            } else {
                int value = validator.compare(best, v);

                if (value == -1) {
                    reporter.report(ctx, v, false);
                }
            }
        } else {
            reporter.report(ctx, v, true);
        }
    }


    @Override
    public void searchValue(@NonNull Closeable ctx, @NonNull ResolveInfo resolveInfo,
                            @NonNull byte[] key, final int quorum) throws ClosedException {

        AtomicInteger numResponses = new AtomicInteger(0);
        AtomicReference<Ipns.Entry> best = new AtomicReference<>();
        getValues(ctx, entry -> processValues(ctx, best.get(), entry, (ctx1, v, better) -> {
            numResponses.incrementAndGet();
            if (better) {
                resolveInfo.resolved(v);
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
                throws ClosedException, ProtocolIssue, TimeoutIssue, RecordIssue, ConnectionIssue;
    }


    public interface RecordValueFunc {
        void record(@NonNull Ipns.Entry entry);
    }

    public interface RecordReportFunc {
        void report(@NonNull Closeable ctx, @NonNull Ipns.Entry entry, boolean better);
    }


    interface Channel {
        void peer(@NonNull AddrInfo addrInfo) throws ClosedException;
    }

}
