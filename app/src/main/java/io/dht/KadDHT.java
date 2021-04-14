package io.dht;

import android.annotation.SuppressLint;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;

import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import dht.pb.Dht;
import io.LogUtils;
import io.core.Closeable;
import io.core.ClosedException;
import io.core.ConnectionFailure;
import io.core.InvalidRecord;
import io.core.ProtocolNotSupported;
import io.core.Validator;
import io.ipfs.IPFS;
import io.ipfs.cid.Cid;
import io.libp2p.AddrInfo;
import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionHandler;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import record.pb.RecordOuterClass;


public class KadDHT implements Routing {
    public static final String Protocol = "/ipfs/kad/1.0.0";
    public static final int defaultBucketSize = 20;

    public static final Duration TempAddrTTL = Duration.ofMinutes(2);
    private static final String TAG = KadDHT.class.getSimpleName();
    private static final int defaultQuorum = 0;
    public final Host host;
    public final PeerId self; // Local peer (yourself)
    public final int beta; // The number of peers closest to a target that must have responded for a query path to terminate
    public final RoutingTable routingTable;
    public final int bucketSize;
    public final int alpha; // The concurrency parameter per path

    private final ID selfKey;
    private final ConcurrentHashMap<PeerId, MessageSender> strmap = new ConcurrentHashMap<>();

    // check todo if replaced by a concrete. better implemenation
    private final QueryFilter filter = (dht, addrInfo) -> addrInfo.hasAddresses();

    private final Validator validator;
    public KadDHT(@NonNull Host host, @NonNull Validator validator, int alpha) {
        this.host = host;
        this.validator = validator;
        this.self = host.getPeerId();
        this.selfKey = Util.ConvertPeerID(host.getPeerId());
        this.bucketSize = defaultBucketSize; // todo config
        this.routingTable = new RoutingTable(bucketSize, selfKey); // TODO
        this.beta = 20; // TODO
        this.alpha = alpha;


        // TODO rethink
        this.host.addConnectionHandler(new ConnectionHandler() {
            @Override
            public void handleConnection(@NotNull Connection conn) {
             // TODO peerFound(conn.secureSession().getRemoteId(), false);
            }
        });
    }

    public void init() {
        // Fill routing table with currently connected peers that are DHT servers
        for (Connection conn : host.getNetwork().getConnections()) {
            peerFound(conn.secureSession().getRemoteId(), false);
        }
    }

    // validRTPeer returns true if the peer supports the DHT protocol and false otherwise. Supporting the DHT protocol means
// supporting the primary protocols, we do not want to add peers that are speaking obsolete secondary protocols to our
// routing table
    boolean validRTPeer(PeerId p) {
        /* TODO
        b, err := dht.peerstore.FirstSupportedProtocol(p, dht.protocolsStrs...)
        if len(b) == 0 || err != nil {
            return false, err
        }

        return dht.routingTablePeerFilter == nil || dht.routingTablePeerFilter(dht, dht.Host().Network().ConnsToPeer(p)), nil*/
        return true;
    }

    // peerFound signals the routingTable that we've found a peer that
// might support the DHT protocol.
// If we have a connection a peer but no exchange of a query RPC ->
//    LastQueriedAt=time.Now (so we don't ping it for some time for a liveliness check)
//    LastUsefulAt=0
// If we connect to a peer and then exchange a query RPC ->
//    LastQueriedAt=time.Now (same reason as above)
//    LastUsefulAt=time.Now (so we give it some life in the RT without immediately evicting it)
// If we query a peer we already have in our Routing Table ->
//    LastQueriedAt=time.Now()
//    LastUsefulAt remains unchanged
// If we connect to a peer we already have in the RT but do not exchange a query (rare)
//    Do Nothing.
    void peerFound(PeerId p, boolean queryPeer) {

        try {
            boolean b = validRTPeer(p);

            if (b) {
                Pair<PeerId, Boolean> entry = Pair.create(p, queryPeer);
                addPeerToRTChan(entry);
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }

    private void addPeerToRTChan(Pair<PeerId, Boolean> entry) {
        int prevSize = routingTable.size();
        int bootstrapCount = 0;
        boolean isBootsrapping = false;
        if (prevSize == 0) {
            isBootsrapping = true;
            bootstrapCount = 0;
        }
        try {
            boolean newlyAdded = routingTable.TryAddPeer(entry.first, entry.second, isBootsrapping);

            if (!newlyAdded && entry.second) {
                // the peer is already in our RT, but we just successfully queried it and so let's give it a
                // bump on the query time so we don't ping it too soon for a liveliness check.
                routingTable.UpdateLastSuccessfulOutboundQueryAt(entry.first, System.currentTimeMillis());
            }

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }


    // GetClosestPeers is a Kademlia 'node lookup' operation. Returns a channel of
    // the K closest peers to the given key.
    //
    // If the context is canceled, this function will return the context error along
    // with the closest K peers it has found so far.
    private Set<PeerId> GetClosestPeers(@NonNull Closeable ctx, @NonNull byte[] key, @NonNull Channel channel) throws ClosedException {
        if (key.length == 0) {
            throw new RuntimeException("can't lookup empty key");
        }

        LookupWithFollowupResult lookupRes = runLookupWithFollowup(ctx, key, new QueryFunc() {
            @NonNull
            @Override
            public List<AddrInfo> func(@NonNull Closeable ctx, @NonNull PeerId p)
                    throws ClosedException, ProtocolNotSupported, ConnectionFailure, InvalidRecord {
                // For DHT query command
                        /* TODO maybe
                        routing.PublishQueryEvent(ctx, &routing.QueryEvent{
                            Type: routing.SendingQuery,
                                    ID:   p,
                        })*/

                Dht.Message pms = findPeerSingle(ctx, p, key);


                List<AddrInfo> peers = new ArrayList<>();
                List<Dht.Message.Peer> list = pms.getCloserPeersList();
                for (Dht.Message.Peer entry : list) {
                    PeerId peerId = new PeerId(entry.getId().toByteArray());


                    List<Multiaddr> multiAddresses = new ArrayList<>();
                    List<ByteString> addresses = entry.getAddrsList();
                    for (ByteString address : addresses) {
                        Multiaddr multiaddr = filterAddress(address);
                        if (multiaddr != null) {
                            multiAddresses.add(multiaddr);
                        }
                    }
                    AddrInfo addrInfo = new AddrInfo(peerId, multiAddresses);
                    if (filter.queryPeerFilter(KadDHT.this, addrInfo)) {
                        peers.add(addrInfo);

                        host.getAddressBook().addAddrs(peerId, Long.MAX_VALUE,
                                addrInfo.getAddresses());
                    }

                }

                for (AddrInfo addrInfo: peers) {
                    channel.invoke(addrInfo.getPeerId());
                }



                // For DHT query command
                        /* TODO maybe
                        routing.PublishQueryEvent(ctx, &routing.QueryEvent{
                            Type:      routing.PeerResponse,
                                    ID:        p,
                                    Responses: peers,
                        }) */

                return peers;
            }
        }, new StopFunc() {
            @Override
            public boolean func() {
                return ctx.isClosed();
            }
        });

        if (ctx.isClosed()) {
            throw new ClosedException();
        }


        if (lookupRes.completed) {
            // refresh the cpl for this key as the query was successful
            // TODO maybe  dht.routingTable.ResetCplRefreshedAtForID(kb.ConvertKey(key), time.Now())
        }

        return lookupRes.asSet();
    }

    @Override
    public int PutValue(@NonNull Closeable ctx, @NonNull byte[] key, @NonNull byte[] value) throws ClosedException {


        // don't even allow local users to put bad values.
        try {
            validator.Validate(key, value);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }

        /* mabye todo
        old, err := dht.getLocal(key)
        if err != nil {
            // Means something is wrong with the datastore.
            return err
        } */

        // Check if we have an old value that's not the same as the new one.
        /* TODO maybe
        if old != nil && !bytes.Equal(old.GetValue(), value) {
            // Check to see if the new one is better.
            i, err := dht.Validator.Select(key, [][]byte{value, old.GetValue()})
            if err != nil {
                return err
            }
            if i != 0 {
                return fmt.Errorf("can't replace a newer value with an older value")
            }
        } */

        @SuppressLint("SimpleDateFormat") String format = new SimpleDateFormat(
                IPFS.TimeFormatIpfs).format(new Date());
        RecordOuterClass.Record rec = RecordOuterClass.Record.newBuilder().setKey(ByteString.copyFrom(key))
                .setValue(ByteString.copyFrom(value))
                .setTimeReceived(format).build();
        /* TODO maybe
        putLocal(key, rec);
        */

        Set<PeerId> res = GetClosestPeers(ctx, key, peerId -> {
            LogUtils.error(TAG, "PutValue " + peerId.toBase58());
        });
        int puts = 0;
        for (PeerId p : res) {
            try {
            /* TODO maybe
        routing.PublishQueryEvent(ctx, &routing.QueryEvent{
            Type: routing.Value,
                    ID:   p,
        }) */

                putValueToPeer(ctx, p, rec);
                puts++;
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }
        return puts;
    }

    private void putValueToPeer(@NonNull Closeable ctx, @NonNull PeerId p, @NonNull RecordOuterClass.Record rec)
            throws ConnectionFailure, ProtocolNotSupported, ClosedException {

        Dht.Message pms = Dht.Message.newBuilder()
                .setType(Dht.Message.MessageType.PUT_VALUE)
                .setKey(rec.getKey())
                .setRecord(rec)
                .setClusterLevelRaw(0).build();
        Dht.Message rpmes = sendRequest(ctx, p, pms);

        if (!Arrays.equals(rpmes.getRecord().getValue().toByteArray(),
                pms.getRecord().getValue().toByteArray())) {
            throw new RuntimeException("value not put correctly put-message  " +
                    pms.toString() + " get-message " + rpmes.toString());
        }
    }

    @Override
    public void FindProvidersAsync(@NonNull Providers providers,
                                   @NonNull Cid cid, int count) throws ClosedException {
        if (!cid.Defined()) {
            return;
        }

        int chSize = count;
        if (count == 0) {
            chSize = 1;
        }


        findProvidersAsyncRoutine(providers, cid.Bytes(), chSize);
    }


    // getLocal attempts to retrieve the value from the datastore
    /*
    func (dht *IpfsDHT) getLocal(key string) (*recpb.Record, error) {
        logger.Debugw("finding value in datastore", "key", loggableRecordKeyString(key))

        rec, err := getRecordFromDatastore(mkDsKey(key))
        if err != nil {
            logger.Warnw("get local failed", "key", loggableRecordKeyString(key), "error", err)
            return nil, err
        }

        // Double check the key. Can't hurt.
        if rec != nil && string(rec.GetKey()) != key {
            logger.Errorw("BUG: found a DHT record that didn't match it's key", "expected", loggableRecordKeyString(key), "got", rec.GetKey())
            return nil, nil

        }
        return rec, nil
    }*/


    // peerStoppedDHT signals the routing table that a peer is unable to responsd to DHT queries anymore.
    // TODO choose better name
    public void peerStoppedDHT(PeerId p) {
        LogUtils.info(TAG , "peer stopped dht " + p.toBase58());
        // A peer that does not support the DHT protocol is dead for us.
        // There's no point in talking to anymore till it starts supporting the DHT protocol again.
        routingTable.RemovePeer(p);
    }

    private void findProvidersAsyncRoutine(@NonNull Providers providers,
                                           @NonNull byte[] key, int count) throws ClosedException {


        boolean findAll = count == 0;
        Set<PeerId> ps = new HashSet<>();

        /*
        Set<AddrInfo> provs = providerManager.GetProviders(cid);
        for (AddrInfo prov : provs) {
            // NOTE: Assuming that this list of peers is unique
            if (ps.add(prov.getPeerId())) {
                providers.Peer(prov);
            }
            // If we have enough peers locally, don't bother with remote RPC
            if (!findAll && ps.size() >= count) {
                return;
            }
        }*/

        // TODO check if correct

        LookupWithFollowupResult lookupRes = runLookupWithFollowup(providers, key,
                new QueryFunc() {

                    @NonNull
                    @Override
                    public List<AddrInfo> func(@NonNull Closeable ctx, @NonNull PeerId p)
                            throws ClosedException, ProtocolNotSupported, ConnectionFailure {
                        // For DHT query command

                        /* TODO
                        routing.PublishQueryEvent(ctx, &routing.QueryEvent{
                            Type: routing.SendingQuery,
                                    ID:   p,
                        })
                        */

                        Dht.Message message = findProvidersSingle(ctx, p, key);

                       /* LogUtils.error(TAG, "" + message.getProviderPeersList().size()
                                + " provider entries");*/

                        List<AddrInfo> provs = new ArrayList<>();
                        List<Dht.Message.Peer> list = message.getProviderPeersList();
                        for (Dht.Message.Peer entry : list) {
                            PeerId peerId = new PeerId(entry.getId().toByteArray());
                            List<Multiaddr> multiAddresses = new ArrayList<>();
                            List<ByteString> addresses = entry.getAddrsList();
                            for (ByteString address : addresses) {
                                Multiaddr multiaddr = filterAddress(address);
                                if (multiaddr != null) {
                                    multiAddresses.add(multiaddr);
                                }
                            }
                            AddrInfo addrInfo = new AddrInfo(peerId, multiAddresses);

                            if (filter.queryPeerFilter(KadDHT.this, addrInfo)) {
                                provs.add(addrInfo);
                                host.getAddressBook().addAddrs(peerId, Long.MAX_VALUE,
                                        addrInfo.getAddresses());
                            }
                        }

                        // LogUtils.error(TAG, "" + provs.size() + " provider entries decoded");

                        // Add unique providers from request, up to 'count'
                        for (AddrInfo prov : provs) {
                            //providerManager.addProvider(cid, prov);

                            LogUtils.error(TAG, "got provider : " + prov.getPeerId());
                            if (ps.add(prov.getPeerId())) {
                                LogUtils.error(TAG, "got provider using: " + prov.getPeerId());
                                providers.Peer(prov);
                            }
                            if (!findAll && ps.size() >= count) {
                                LogUtils.error(TAG, "got provider enough " + ps.size() + " " + count);
                                break;
                            }
                        }

                        // Give closer peers back to the query to be queried
                        List<AddrInfo> peers = new ArrayList<>();
                        List<Dht.Message.Peer> closerPeersList = message.getCloserPeersList();
                        for (Dht.Message.Peer entry : closerPeersList) {
                            PeerId peerId = new PeerId(entry.getId().toByteArray());
                            List<Multiaddr> multiAddresses = new ArrayList<>();
                            List<ByteString> addresses = entry.getAddrsList();
                            for (ByteString address : addresses) {
                                Multiaddr multiaddr = filterAddress(address);
                                if (multiaddr != null) {
                                    multiAddresses.add(multiaddr);
                                }
                            }
                            AddrInfo addrInfo = new AddrInfo(peerId, multiAddresses);
                            if (filter.queryPeerFilter(KadDHT.this, addrInfo)) {
                                peers.add(addrInfo);
                                host.getAddressBook().addAddrs(peerId, Long.MAX_VALUE,
                                        addrInfo.getAddresses());
                            }
                        }

                        /*
                        routing.PublishQueryEvent(ctx, &routing.QueryEvent{
                            Type:      routing.PeerResponse,
                                    ID:        p,
                                    Responses: peers,
                        })*/

                        return peers;

                    }
                }, new StopFunc() {
                    @Override
                    public boolean func() {
                        return providers.isClosed() || (!findAll && ps.size() >= count);
                    }
                });

        if (providers.isClosed()) {
            throw new ClosedException();
        }
        /*  TODO
        if( err == nil && ctx.Err() == nil) {
            dht.refreshRTIfNoShortcut(kb.ConvertKey(string(key)), lookupRes)
        }*/
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
    public int Provide(@NonNull Closeable ctx, @NonNull Cid key) throws ClosedException {

        if (!key.Defined()) {
            throw new RuntimeException("invalid cid: undefined");
        }

        byte[] keyMH = key.Bytes();


        /* TODO maybe
        closerCtx := ctx
        if deadline, ok := ctx.Deadline(); ok {
            now := time.Now()
            timeout := deadline.Sub(now)

            if timeout < 0 {
                // timed out
                return context.DeadlineExceeded
            } else if timeout < 10*time.Second {
                // Reserve 10% for the final put.
                deadline = deadline.Add(-timeout / 10)
            } else {
                // Otherwise, reserve a second (we'll already be
                // connected so this should be fast).
                deadline = deadline.Add(-time.Second)
            }
            var cancel context.CancelFunc
                    closerCtx, cancel = context.WithDeadline(ctx, deadline)
            defer cancel()
        }*/


        Set<PeerId> peers = GetClosestPeers(ctx, keyMH, new Channel() {
            @Override
            public void invoke(@NonNull PeerId peerId) {
                LogUtils.error(TAG, "Provide " + peerId.toBase58());
            }
        });

        int publish = 0;
        Dht.Message mes = makeProvRecord(keyMH);
        for (PeerId p : peers) {
            try {
                sendRequest(ctx, p, mes);
                publish++;
            } catch (ClosedException closedException) {
                throw closedException;

            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }
        return publish;
    }

    // sendRequest sends out a request, but also makes sure to
    // measure the RTT for latency measurements.
    private Dht.Message sendRequest(@NonNull Closeable ctx,
                                    @NonNull PeerId peerId,
                                    @NonNull Dht.Message message)
            throws ClosedException, ProtocolNotSupported, ConnectionFailure {
        //ctx, _ = tag.New(ctx, metrics.UpsertMessageType(pmes)) // TODO maybe

        MessageSender ms = messageSenderForPeer(peerId);

        /* TODO
        if err != nil {
            stats.Record(ctx,
                    metrics.SentRequests.M(1),
                    metrics.SentRequestErrors.M(1),
                    )
            logger.Debugw("request failed to open message sender", "error", err, "to", p)
            return nil, err
        }*/


        long start = System.currentTimeMillis();

        Dht.Message response = ms.SendRequest(ctx, message);
        /* TODO
        if err != nil {
            stats.Record(ctx,
                    metrics.SentRequests.M(1),
                    metrics.SentRequestErrors.M(1),
                    )
            logger.Debugw("request failed", "error", err, "to", p)
            return nil, err
        }*/

        /* TODO
        stats.Record(ctx,
                metrics.SentRequests.M(1),
                metrics.SentBytes.M(int64(pmes.Size())),
                metrics.OutboundRequestLatency.M(float64(time.Since(start))/float64(time.Millisecond)),
                )*/
        // TODO peerstore.RecordLatency(p, time.Since(start))
        return response;
    }

    private MessageSender messageSenderForPeer(@NonNull PeerId p) {
        MessageSender ms = strmap.get(p);
        if (ms != null) {
            return ms;
        }
        ms = new MessageSender(p, this);
        strmap.put(p, ms);
        return ms;
    }


    private Dht.Message getValueSingle(@NonNull Closeable ctx, @NonNull PeerId p, @NonNull byte[] key)
            throws ConnectionFailure, ProtocolNotSupported, ClosedException {
        Dht.Message pms = Dht.Message.newBuilder()
                .setType(Dht.Message.MessageType.GET_VALUE)
                .setKey(ByteString.copyFrom(key))
                .setClusterLevelRaw(0).build();
        return sendRequest(ctx, p, pms);
    }

    private Dht.Message findPeerSingle(@NonNull Closeable ctx, @NonNull PeerId p, @NonNull byte[] key)
            throws ClosedException, ProtocolNotSupported, ConnectionFailure {
        Dht.Message pms = Dht.Message.newBuilder()
                .setType(Dht.Message.MessageType.FIND_NODE)
                .setKey(ByteString.copyFrom(key))
                .setClusterLevelRaw(0).build();

        return sendRequest(ctx, p, pms);
    }

    private Dht.Message findProvidersSingle(@NonNull Closeable ctx, @NonNull PeerId p, @NonNull byte[] cid)
            throws ClosedException, ProtocolNotSupported, ConnectionFailure {
        Dht.Message pms = Dht.Message.newBuilder()
                .setType(Dht.Message.MessageType.GET_PROVIDERS)
                .setKey(ByteString.copyFrom(cid))
                .setClusterLevelRaw(0).build();
        return sendRequest(ctx, p, pms);
    }

    // FindLocal looks for a peer with a given ID connected to this dht and returns the peer and the table it was found in.
    @Nullable
    AddrInfo FindLocal(@NonNull PeerId id) {
        // TODO optimize (also just one address)
        /*
        switch dht.host.Network().Connectedness(id) {
	case network.Connected, network.CanConnect:
		return dht.peerstore.PeerInfo(id)
	default:
		return peer.AddrInfo{}
	}

         */
        List<Connection> cons = host.getNetwork().getConnections();
        for (Connection con : cons) {
            if (Objects.equals(con.secureSession().getRemoteId(), id)) {
                return new AddrInfo(id, con.remoteAddress());
            }
        }
        return null;
    }

    @Nullable
    private Multiaddr filterAddress(@NonNull ByteString address) {
        try {
           Multiaddr multiaddr = new Multiaddr(address.toByteArray());
          // LogUtils.info(TAG, multiaddr.toString());
           return multiaddr;
        } catch (Throwable ignore){
            // nothing to do
        }
        return null;
    }

    @Override
    public AddrInfo FindPeer(@NonNull Closeable closeable, @NonNull PeerId id) throws ClosedException {

        // Check if were already connected to them
        AddrInfo pi = FindLocal(id);
        if (pi != null) {
            return pi;
        }
            /*
            if pi := dht.FindLocal(id); pi.ID != "" {
                return pi, nil
            }*/

        LookupWithFollowupResult lookupRes = runLookupWithFollowup(closeable, id.getBytes(),
                new QueryFunc() {
                    @NonNull
                    @Override
                    public List<AddrInfo> func(@NonNull Closeable ctx, @NonNull PeerId p)
                            throws ClosedException, ProtocolNotSupported, ConnectionFailure {
                            /* TODO
                            // For DHT query command
                            routing.PublishQueryEvent(ctx, &routing.QueryEvent{
                                Type: routing.SendingQuery,
                                        ID:   p,
                            })*/

                        Dht.Message pms = findPeerSingle(ctx, p, id.getBytes());

                        List<AddrInfo> peers = new ArrayList<>();
                        List<Dht.Message.Peer> list = pms.getCloserPeersList();
                        for (Dht.Message.Peer entry : list) {
                            PeerId peerId = new PeerId(entry.getId().toByteArray());


                            List<Multiaddr> multiAddresses = new ArrayList<>();
                            List<ByteString> addresses = entry.getAddrsList();
                            for (ByteString address : addresses) {
                                Multiaddr multiaddr = filterAddress(address);
                                if (multiaddr != null) {
                                    multiAddresses.add(multiaddr);
                                }
                            }
                            AddrInfo addrInfo = new AddrInfo(peerId, multiAddresses);
                            if (filter.queryPeerFilter(KadDHT.this, addrInfo)) {
                                peers.add(addrInfo);

                                host.getAddressBook().addAddrs(peerId, Long.MAX_VALUE,
                                        addrInfo.getAddresses());
                            }

                        }


                            /* TODO
                            // For DHT query command
                            routing.PublishQueryEvent(ctx, &routing.QueryEvent{
                                Type:      routing.PeerResponse,
                                        ID:        p,
                                        Responses: peers,
                            })*/

                        return peers;
                    }
                }, new StopFunc() {
                    @Override
                    public boolean func() {
                        try {
                            // return dht.host.Network().Connectedness(id) == network.Connected
                            return closeable.isClosed() || host.getNetwork().connect(id).get() != null;
                        } catch (Throwable throwable) {
                            return false;
                        }
                    }
                });


        if (closeable.isClosed()) {
            throw new ClosedException();
        }
        try {
            boolean dialedPeerDuringQuery = false;
            Objects.requireNonNull(lookupRes);
            PeerState state = lookupRes.peers.get(id);
            if (state != null) {
                // Note: we consider PeerUnreachable to be a valid state because the peer may not support the DHT protocol
                // and therefore the peer would fail the query. The fact that a peer that is returned can be a non-DHT
                // server peer and is not identified as such is a bug.
                dialedPeerDuringQuery = (state == PeerState.PeerQueried || state == PeerState.PeerUnreachable || state == PeerState.PeerWaiting);

            }

            // Return peer information if we tried to dial the peer during the query or we are
            // (or recently were) connected to the peer.

            Collection<Multiaddr> addr = host.getAddressBook().getAddrs(id).get();
            Objects.requireNonNull(addr);
            AddrInfo addrInfo = new AddrInfo(id, addr);

            boolean connectedness = host.getNetwork().connect(
                    addrInfo.getPeerId(), addrInfo.getAddresses()).get() != null;

            if (dialedPeerDuringQuery || connectedness) {
                return addrInfo;
            }
        } catch (Throwable ignore) {
        }

        return null;
    }

    private LookupWithFollowupResult runQuery(@NonNull Closeable ctx, @NonNull byte[] target,
                                              @NonNull QueryFunc queryFn, @NonNull StopFunc stopFn)
            throws ClosedException, InterruptedException {
        // pick the K closest peers to the key in our Routing table.
        ID targetKadID = Util.ConvertKey(target);
        List<PeerId> seedPeers = routingTable.NearestPeers(targetKadID, bucketSize);
        if (seedPeers.size() == 0) {
            /* TODO
            routing.PublishQueryEvent(ctx, &routing.QueryEvent{
                Type:  routing.QueryError,
                        Extra: kb.ErrLookupFailure.Error(),
            })
            return nil, kb.ErrLookupFailure */
            throw new RuntimeException(); // TODO ErrLookupFailure
        }

        Query q = new Query(this, UUID.randomUUID(), target, seedPeers,
                QueryPeerSet.create(target), queryFn, stopFn);

        // run the query
        q.run(ctx);


        if (ctx.isClosed()) {
            throw new ClosedException(); // TODO maybe not necessary
        }


        q.recordValuablePeers();


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
        // run the query

        try {
            LookupWithFollowupResult lookupRes = runQuery(ctx, target, queryFn, stopFn);


            // query all of the top K peers we've either Heard about or have outstanding queries we're Waiting on.
            // This ensures that all of the top K results have been queried which adds to resiliency against churn for query
            // functions that carry state (e.g. FindProviders and GetValue) as well as establish connections that are needed
            // by stateless query functions (e.g. GetClosestPeers and therefore Provide and PutValue)

            /* TODO maybe

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



        // return if the lookup has been externally stopped
        if ctx.Err() != nil || stopFn() {
            lookupRes.completed = false
            return lookupRes, nil
        }

        doneCh := make(chan struct{}, len(queryPeers))
        followUpCtx, cancelFollowUp := context.WithCancel(ctx)
        defer cancelFollowUp()
        for _, p := range queryPeers {
            qp := p
            go func() {
                _, _ = queryFn(followUpCtx, qp)
                doneCh <- struct{}{}
            }()
        }

        // wait for all queries to complete before returning, aborting ongoing queries if we've been externally stopped
        followupsCompleted := 0
        processFollowUp:
        for i := 0; i < len(queryPeers); i++ {
            select {
                case <-doneCh:
                    followupsCompleted++
                    if stopFn() {
                    cancelFollowUp()
                    if i < len(queryPeers)-1 {
                        lookupRes.completed = false
                    }
                    break processFollowUp
                }
                case <-ctx.Done():
                    lookupRes.completed = false
                    cancelFollowUp()
                    break processFollowUp
            }
        }

        if !lookupRes.completed {
            for i := followupsCompleted; i < len(queryPeers); i++ {
			<-doneCh
            }
        }
        */
            return lookupRes;
        } catch (InterruptedException ignore) {
            throw new ClosedException();
        }

    }



    // getValueOrPeers queries a particular peer p for the value for
// key. It returns either the value or a list of closer peers.
// NOTE: It will update the dht's peerstore with any new addresses
// it finds for the given peer.
    private Pair< RecordOuterClass.Record, List<AddrInfo>> getValueOrPeers(@NonNull Closeable ctx,
                                                                           @NonNull PeerId p,
                                                                           @NonNull byte[] key)
            throws ConnectionFailure, ClosedException, ProtocolNotSupported, InvalidRecord { // TODO rethink InvalidRecord


        Dht.Message pms = getValueSingle(ctx, p, key);

        // Perhaps we were given closer peers
        List<AddrInfo> peers = new ArrayList<>();
        List<Dht.Message.Peer> list = pms.getCloserPeersList();
        for (Dht.Message.Peer entry : list) {
            PeerId peerId = new PeerId(entry.getId().toByteArray());


            List<Multiaddr> multiAddresses = new ArrayList<>();
            List<ByteString> addresses = entry.getAddrsList();
            for (ByteString address : addresses) {
                Multiaddr multiaddr = filterAddress(address);
                if (multiaddr != null) {
                    multiAddresses.add(multiaddr);
                }
            }
            AddrInfo addrInfo = new AddrInfo(peerId, multiAddresses);
            if (filter.queryPeerFilter(KadDHT.this, addrInfo)) {
                peers.add(addrInfo);
                // TODO check if address does not contains multiple same entries
                host.getAddressBook().addAddrs(peerId, Long.MAX_VALUE,
                        addrInfo.getAddresses());
            }

        }


        if(pms.hasRecord()) {
            RecordOuterClass.Record rec = pms.getRecord();
            // make sure record is valid.
            try {
                byte[] record = rec.getValue().toByteArray();
                if(record != null && record.length > 0) {
                    LogUtils.error(TAG, "Got Record ");
                    validator.Validate(rec.getKey().toByteArray(), record);
                    LogUtils.error(TAG, "Got Record success for key ");
                    return Pair.create(rec, peers);
                }
            } catch (Throwable throwable){
                LogUtils.error(TAG, "" + throwable.getMessage());
            }
        }


        if( peers.size() > 0 ) {
            return Pair.create(null, peers);
        }
        return Pair.create(null, Collections.emptyList());
    }

    LookupWithFollowupResult getValues(@NonNull Closeable ctx, @NonNull RecordValFunc recordFunc,
                                       @NonNull byte[] key, @NonNull StopFunc stopQuery) throws ClosedException {

        /*
        valCh := make(chan RecvdVal, 1)
        lookupResCh := make(chan *lookupWithFollowupResult, 1)

        logger.Debugw("finding value", "key", loggableRecordKeyString(key))

        if rec, err := dht.getLocal(key); rec != nil && err == nil {
            select {
                case valCh <- RecvdVal{
                    Val:  rec.GetValue(),
                            From: dht.self,
                }:
                case <-ctx.Done():
            }
        }*/

            LookupWithFollowupResult lookupRes = runLookupWithFollowup(ctx, key,
                    new QueryFunc() {
                        @NonNull
                        @Override
                        public List<AddrInfo> func(@NonNull Closeable ctx, @NonNull PeerId p)
                                throws ClosedException, ProtocolNotSupported, ConnectionFailure, InvalidRecord {

                            // For DHT query command
                            /* maybe todo
                            routing.PublishQueryEvent(ctx, &routing.QueryEvent{
                                Type: routing.SendingQuery,
                                        ID:   p,
                            }) */

                            Pair< RecordOuterClass.Record, List<AddrInfo>> result = getValueOrPeers(ctx, p, key);
                            RecordOuterClass.Record rec = result.first;
                            List<AddrInfo> peers = result.second;
                            /* maybe todo
                            switch err {
                                case routing.ErrNotFound:
                                    // in this case, they responded with nothing,
                                    // still send a notification so listeners can know the
                                    // request has completed 'successfully'
                                    routing.PublishQueryEvent(ctx, &routing.QueryEvent{
                                    Type: routing.PeerResponse,
                                            ID:   p,
                                })
                                return nil, err
                                default:
                                    return nil, err
                                case nil, errInvalidRecord:
                                    // in either of these cases, we want to keep going
                            } */

                            // TODO: What should happen if the record is invalid?
                            // Pre-existing code counted it towards the quorum, but should it?



                            if(rec != null){
                                recordFunc.func(new RecordInfo(p, rec.getValue().toByteArray()));
                            }


                            // For DHT query command
                            /* maybe TODO
                            routing.PublishQueryEvent(ctx, &routing.QueryEvent{
                                Type:      routing.PeerResponse,
                                        ID:        p,
                                        Responses: peers,
                            })*/

                            return peers;
                        }
                    }, stopQuery );


        /* TODO
            if err != nil {
                return
            }
            lookupResCh <- lookupRes

            if ctx.Err() == nil {
                dht.refreshRTIfNoShortcut(kb.ConvertKey(key), lookupRes)
            }
       */

        return lookupRes;
    }


    @NonNull
    private RecordValResult processValues(@NonNull Closeable ctx,
                                          @Nullable RecordInfo best,
                                          @NonNull RecordInfo v,
                                          @NonNull RecordReportFunc newVal) {

        RecordValResult result = new RecordValResult();

                // Select best value
                if(best != null) {
                    if(Arrays.equals(best.Val, v.Val)) {
                        result.peersWithBest.add(v.From); // TODO
                        result.aborted = newVal.func(ctx, v, false);
                    } else {
                        int value = validator.Select(best.Val, v.Val);

                        if (value == -1) {
                            result.aborted = newVal.func(ctx, v, false);
                        }
                    }
                } else {

                    result.peersWithBest.add(v.From); // TODO
                    result.aborted = newVal.func(ctx, v, true);

                }

        return result;
    }


    @Override
    public void SearchValue(@NonNull Closeable ctx, @NonNull ResolveInfo resolveInfo,
                            @NonNull byte[] key, Option... options) throws ClosedException {

        boolean offline = false;
        int quorum = defaultQuorum;

        for (Option option : options) {
            if (option instanceof Offline) {
                offline = ((Offline) option).isOffline();
            }
            if (option instanceof Quorum) {
                quorum = ((Quorum) option).getQuorum();
            }
        }

        int responsesNeeded = 0;
        if (!offline) {
            responsesNeeded = quorum;
        }
        final int nvals = responsesNeeded;
        AtomicInteger numResponses = new AtomicInteger(0);
        AtomicReference<RecordInfo> best = new AtomicReference<>();
        LookupWithFollowupResult lookupRes = getValues(ctx, new RecordValFunc() {

            @Override
            public void func(@NonNull RecordInfo recordVal) {

                RecordValResult res = processValues(ctx, best.get(), recordVal, new RecordReportFunc() {
                    @Override
                    public boolean func(Closeable ctx, RecordInfo v, boolean better) {
                        numResponses.incrementAndGet();
                        if (better) {
                            resolveInfo.resolved(v.Val);
                            best.set(v);
                        }

                        return nvals > 0 && (numResponses.get() > nvals);
                    }
                });

                if(res == null){
                    return;
                }

                if( res.best == null || res.aborted  ){
                    return;
                }
            /* TODO
            updatePeers := make([]peer.ID, 0, bucketSize)
            select {
                case l := <-lookupRes:
                    if l == nil {
                    return
                }

                for _, p := range l.peers {
                    if _, ok := peersWithBest[p]; !ok {
                        updatePeers = append(updatePeers, p)
                    }
                }
                case <-ctx.Done():
                    return
            }

            updatePeerValues(ctx, key, best, updatePeers) */
            }
        }, key, new StopFunc() {
            @Override
            public boolean func() {
                return numResponses.get() == nvals;
            }
        });



    }

}
