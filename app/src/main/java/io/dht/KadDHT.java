package io.dht;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.Closeable;
import io.LogUtils;
import io.ipfs.ClosedException;
import io.ipfs.cid.Cid;
import io.libp2p.core.Connection;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.protos.dht.DhtProtos;


public class KadDHT implements Routing {
    public static final String Protocol = "/ipfs/kad/1.0.0";
    public static final Duration TempAddrTTL = Duration.ofMinutes(2);
    private static final String TAG = KadDHT.class.getSimpleName();
    public final Host host;
    private final ProviderManager providerManager = new ProviderManager();

    private boolean enableProviders = true;
    private boolean enableValues = true;

    public KadDHT(@NonNull Host host) {
        this.host = host;
    }


    @Override
    public void PutValue(@NonNull Closeable closable, String key, byte[] data) {

        LogUtils.error(TAG, key);
    }

    @Override
    public void FindProvidersAsync(@NonNull Providers providers,
                                   @NonNull Cid cid, int count) throws ClosedException {
        if(!enableProviders || !cid.Defined()) {
            return;
        }

        int chSize = count;
        if (count == 0) {
            chSize = 1;
        }


        findProvidersAsyncRoutine(providers, cid, chSize);
    }


    private void maybeAddAddrs(@NonNull PeerId p, @NonNull List<Multiaddr> addrs) {
        // Don't add addresses for self or our connected peers. We have better ones.
        /* TODO
        if( Objects.equals(p,self) || host.getNetwork().Connectedness(p) == network.Connected ) {
            return;
        }*/
        for (Multiaddr addr : addrs) {
            host.getAddressBook().addAddrs(p, KadDHT.TempAddrTTL.toMillis(), addr);
        }
    }

    private void findProvidersAsyncRoutine(@NonNull Providers providers,
                                           @NonNull Cid cid, int count) throws ClosedException {


        boolean findAll = count == 0;
        Set<PeerId> ps = new HashSet<>();


        Set<PeerId> provs = providerManager.GetProviders(cid);
        for (PeerId prov : provs) {
            // NOTE: Assuming that this list of peers is unique
            if (ps.add(prov)) {
                // TODO Providers should have AddrInfo
                //  pi = peerstore.PeerInfo(p);

                providers.Peer(prov.toBase58()); // TODO can be wrong

            }
            // If we have enough peers locally, don't bother with remote RPC
            // TODO: is this a DOS vector?
            if (!findAll && ps.size() >= count) {
                return;
            }
        }

        // TODO check if correct
        io.ipfs.multihash.Multihash key = cid.Hash();
        LookupWithFollowupResult lookupRes = runLookupWithFollowup(providers, key.toHex(),
                new QueryFunc() {

                    @NonNull
                    @Override
                    public List<AddrInfo> func(@NonNull Closeable ctx, @NonNull PeerId p) throws ClosedException {
                        // For DHT query command

                        /* TODO
                        routing.PublishQueryEvent(ctx, &routing.QueryEvent{
                            Type: routing.SendingQuery,
                                    ID:   p,
                        })
                        */

                        DhtProtos.Message pmes = findProvidersSingle(ctx, p, key);

                        LogUtils.error(TAG, "" + pmes.getProviderPeersList().size()
                                + " provider entries");

                        List<AddrInfo> provs = new ArrayList<>();
                        List<DhtProtos.Message.Peer> list = pmes.getProviderPeersList();
                        for (DhtProtos.Message.Peer entry : list) {
                            PeerId peerId = new PeerId(entry.getId().toByteArray());
                            List<Multiaddr> multiAddresses = new ArrayList<>();
                            List<ByteString> addresses = entry.getAddrsList();
                            for (ByteString address : addresses) {
                                multiAddresses.add(new Multiaddr(address.toByteArray()));
                            }
                            provs.add(new AddrInfo(peerId, multiAddresses));
                        }

                        LogUtils.error(TAG, "" + provs.size() + " provider entries decoded");

                        // Add unique providers from request, up to 'count'
                        for (AddrInfo prov : provs) {

                            maybeAddAddrs(prov.ID, prov.getAddresses());
                            LogUtils.error(TAG, "got provider : " + prov.ID);
                            if (ps.add(prov.ID)) {
                                LogUtils.error(TAG, "using provider: " + prov.ID);

                                providers.Peer(prov.ID.toBase58()); // TODO can be wrong

                            }
                            if (!findAll && ps.size() >= count) {
                                LogUtils.error(TAG, "got enough providers " + ps.size() + " " + count);
                                break;
                            }
                        }

                        // Give closer peers back to the query to be queried
                        List<AddrInfo> peers = new ArrayList<>();
                        List<DhtProtos.Message.Peer> closerPeersList = pmes.getCloserPeersList();
                        for (DhtProtos.Message.Peer entry : closerPeersList) {
                            PeerId peerId = new PeerId(entry.getId().toByteArray());
                            List<Multiaddr> multiAddresses = new ArrayList<>();
                            List<ByteString> addresses = entry.getAddrsList();
                            for (ByteString address : addresses) {
                                multiAddresses.add(new Multiaddr(address.toByteArray()));
                            }
                            peers.add(new AddrInfo(peerId, multiAddresses));
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

        if(providers.isClosed()){
            throw new ClosedException();
        }
        /*  TODO
        if( err == nil && ctx.Err() == nil) {
            dht.refreshRTIfNoShortcut(kb.ConvertKey(string(key)), lookupRes)
        }*/
    }

    @Override
    public void Provide(@NonNull Closeable closeable, @NonNull Cid cid) {

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


    // sendRequest sends out a request, but also makes sure to
// measure the RTT for latency measurements.
    private DhtProtos.Message sendRequest(Closeable ctx, PeerId p, DhtProtos.Message pmes) throws ClosedException {
        //ctx, _ = tag.New(ctx, metrics.UpsertMessageType(pmes)) // TODO maybe

        MessageSender ms = messageSenderForPeer(ctx, p);

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

        DhtProtos.Message response = ms.SendRequest(ctx, pmes);
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

    private final ConcurrentHashMap<PeerId, MessageSender> strmap = new ConcurrentHashMap<>();

    private MessageSender messageSenderForPeer(@NonNull Closeable ctx, @NonNull PeerId p) {
        MessageSender ms = strmap.get(p);
        if(ms != null) {
            return ms;
        }

        ms = new MessageSender(p, this);
        strmap.put(p, ms);

        try {
            ms.prepOrInvalidate(ctx);
        } catch (Throwable throwable){

            // Not changed, remove the now invalid stream from the
            // map.
            strmap.remove(p);
            throw new RuntimeException(throwable);
        }
        /* TODO
        if err := ms.prepOrInvalidate(ctx); err != nil {
            dht.smlk.Lock()
            defer dht.smlk.Unlock()

            if msCur, ok := dht.strmap[p]; ok {
                // Changed. Use the new one, old one is invalid and
                // not in the map so we can just throw it away.
                if ms != msCur {
                    return msCur, nil
                }
                // Not changed, remove the now invalid stream from the
                // map.
                delete(dht.strmap, p)
            }
            // Invalid but not in map. Must have been removed by a disconnect.
            return nil, err
        }*/
        // All ready to go.
        return ms;
    }

    // findPeerSingle asks peer 'p' if they know where the peer with id 'id' is
    private DhtProtos.Message findPeerSingle(Closeable ctx, PeerId p, PeerId id)
            throws ClosedException {
        DhtProtos.Message pmes = DhtProtos.Message.newBuilder()
                .setType(DhtProtos.Message.MessageType.FIND_NODE)
                .setKey(ByteString.copyFrom(id.getBytes())).build();

        return sendRequest(ctx, p, pmes);
    }


    private DhtProtos.Message findProvidersSingle(Closeable ctx, PeerId p, io.ipfs.multihash.Multihash key)
            throws ClosedException {
        DhtProtos.Message pmes = DhtProtos.Message.newBuilder()
                .setType(DhtProtos.Message.MessageType.GET_PROVIDERS)
                .setKey(ByteString.copyFrom(key.getHash())).build();
        return sendRequest(ctx, p, pmes);
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
        for (Connection con:cons) {
            if(Objects.equals(con.secureSession().getRemoteId(), id)){
                return new AddrInfo(id, con.remoteAddress());
            }
        }
        return null;
    }

    @Override
    public AddrInfo FindPeer(@NonNull Closeable closeable, @NonNull PeerId id) {


        LogUtils.error(TAG, "finding peer " + id.toBase58());

        // Check if were already connected to them
        AddrInfo pi = FindLocal(id);
        if (pi != null) {
            return pi;
        }
            /*
            if pi := dht.FindLocal(id); pi.ID != "" {
                return pi, nil
            }*/

            LookupWithFollowupResult lookupRes = runLookupWithFollowup(closeable, id.toBase58(),
                    new QueryFunc() {
                        @NonNull
                        @Override
                        public List<AddrInfo> func(@NonNull Closeable ctx, @NonNull PeerId p) throws ClosedException {
                            /* TODO
                            // For DHT query command
                            routing.PublishQueryEvent(ctx, &routing.QueryEvent{
                                Type: routing.SendingQuery,
                                        ID:   p,
                            })*/

                            DhtProtos.Message pmes = findPeerSingle(ctx, p, id);

                            List<AddrInfo> peers = new ArrayList<>();
                            List<DhtProtos.Message.Peer> list = pmes.getCloserPeersList();
                            for (DhtProtos.Message.Peer entry : list) {
                                PeerId peerId = new PeerId(entry.getId().toByteArray());
                                List<Multiaddr> multiAddresses = new ArrayList<>();
                                List<ByteString> addresses = entry.getAddrsList();
                                for (ByteString address : addresses) {
                                    multiAddresses.add(new Multiaddr(address.toByteArray()));
                                }
                                peers.add(new AddrInfo(peerId, multiAddresses));
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
                            } catch (Throwable throwable){
                                return false;
                            }
                        }
                    });


            boolean dialedPeerDuringQuery = false;
            PeerState state = lookupRes.peers.get(id);
            if(state != null){
                // Note: we consider PeerUnreachable to be a valid state because the peer may not support the DHT protocol
                // and therefore the peer would fail the query. The fact that a peer that is returned can be a non-DHT
                // server peer and is not identified as such is a bug.
                dialedPeerDuringQuery = (state == PeerState.PeerQueried || state == PeerState.PeerUnreachable || state == PeerState.PeerWaiting);

            }
            /*
            for i, p := range lookupRes.peers {
                if p == id {
                    // Note: we consider PeerUnreachable to be a valid state because the peer may not support the DHT protocol
                    // and therefore the peer would fail the query. The fact that a peer that is returned can be a non-DHT
                    // server peer and is not identified as such is a bug.
                    dialedPeerDuringQuery = (lookupRes.state[i] == qpeerset.PeerQueried || lookupRes.state[i] == qpeerset.PeerUnreachable || lookupRes.state[i] == qpeerset.PeerWaiting)
                    break
                }
            }*/
            
            // Return peer information if we tried to dial the peer during the query or we are (or recently were) connected
            // to the peer.
            //connectedness := dht.host.Network().Connectedness(id)
            try {
                boolean connectedness = host.getNetwork().connect(id).get() != null;
                /*if (dialedPeerDuringQuery || connectedness == network.Connected || connectedness == network.CanConnect) {
                    return dht.peerstore.PeerInfo(id),nil
                }*/
                if(dialedPeerDuringQuery || connectedness){
                    return new AddrInfo(id); // TODO shoud use peerstore
                    //return new PeerInfo(id);
                }
            } catch (Throwable throwable){
                LogUtils.error(TAG, throwable); // TODO
            }

        return null;
    }


    // runLookupWithFollowup executes the lookup on the target using the given query function and stopping when either the
// context is cancelled or the stop function returns true. Note: if the stop function is not sticky, i.e. it does not
// return true every time after the first time it returns true, it is not guaranteed to cause a stop to occur just
// because it momentarily returns true.
//
// After the lookup is complete the query function is run (unless stopped) against all of the top K peers from the
// lookup that have not already been successfully queried.
    private LookupWithFollowupResult runLookupWithFollowup(@NonNull Closeable ctx, @NonNull String target,
                                                           @NonNull QueryFunc queryFn, @NonNull StopFunc stopFn) {
        // run the query
        /*
        lookupRes, err := dht.runQuery(ctx, target, queryFn, stopFn)
        if err != nil {
            return nil, err
        }

        // query all of the top K peers we've either Heard about or have outstanding queries we're Waiting on.
        // This ensures that all of the top K results have been queried which adds to resiliency against churn for query
        // functions that carry state (e.g. FindProviders and GetValue) as well as establish connections that are needed
        // by stateless query functions (e.g. GetClosestPeers and therefore Provide and PutValue)
        queryPeers := make([]peer.ID, 0, len(lookupRes.peers))
        for i, p := range lookupRes.peers {
            if state := lookupRes.state[i]; state == qpeerset.PeerHeard || state == qpeerset.PeerWaiting {
                queryPeers = append(queryPeers, p)
            }
        }

        if len(queryPeers) == 0 {
            return lookupRes, nil
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

        return lookupRes, nil*/
        return null;
    }

    private static int defaultQuorum = 0;

    @Override
    public void SearchValue(@NonNull ResolveInfo resolveInfo, @NonNull String key, Option... options) {

        if( !enableValues) {
            throw new RuntimeException();
        }

        boolean offline = false;
        int quorum = defaultQuorum;

        for (Option option:options) {
            if(option instanceof Offline){
                offline = ((Offline) option).isOffline();
            }
            if(option instanceof Quorum){
                quorum = ((Quorum) option).getQuorum();
            }
        }

        int responsesNeeded = 0;
        if(!offline) {
            responsesNeeded = quorum;
        }
        /*
        stopCh := make(chan struct{})
        valCh, lookupRes := getValues(ctx, key, stopCh)

        out := make(chan []byte)
        go func() {
            defer close(out)
                    best, peersWithBest, aborted := searchValueQuorum(ctx, key, valCh, stopCh, out, responsesNeeded)
            if best == nil || aborted {
                return
            }

            updatePeers := make([]peer.ID, 0, dht.bucketSize)
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

            dht.updatePeerValues(dht.Context(), key, best, updatePeers)
        }()*/
    }
}
