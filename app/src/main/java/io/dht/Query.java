package io.dht;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.common.collect.Iterables;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.LogUtils;
import io.core.Closeable;
import io.core.ClosedException;
import io.core.ConnectionFailure;
import io.core.ProtocolNotSupported;
import io.ipfs.IPFS;
import io.libp2p.AddrInfo;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;

public class Query {

    private static final String TAG = Query.class.getSimpleName();
    private final UUID id;

    // target key for the lookup
    private final String key;

    // the query context.
    // ctx context.Context

    private final KadDHT dht;

    // seedPeers is the set of peers that seed the query
    private final List<PeerId> seedPeers;

    // peerTimes contains the duration of each successful query to a peer
    private final ConcurrentHashMap<PeerId, Long> peerTimes = new ConcurrentHashMap<>();

    // queryPeers is the set of peers known by this query and their respective states.
    private final QueryPeerSet queryPeers;
    // the function that will be used to query a single peer.
    private final QueryFunc queryFn;

    // stopFn is used to determine if we should stop the WHOLE disjoint query.
    private final StopFunc stopFn;
    // terminated is set when the first worker thread encounters the termination condition.
    // Its role is to make sure that once termination is determined, it is sticky.
    boolean terminated = false;

    public Query(@NonNull KadDHT dht, @NonNull UUID uuid, @NonNull byte[] key,
                 @NonNull List<PeerId> seedPeers, @NonNull QueryPeerSet queryPeers,
                 @NonNull QueryFunc queryFn, @NonNull StopFunc stopFn) {
        this.id = uuid;
        this.key = new String(key); // TODO remove
        this.dht = dht;
        this.seedPeers = seedPeers;
        this.queryPeers = queryPeers;
        this.queryFn = queryFn;
        this.stopFn = stopFn;
    }


    public LookupWithFollowupResult constructLookupResult(@NonNull ID target) {

        // determine if the query terminated early
        boolean completed = true;

        // Lookup and starvation are both valid ways for a lookup to complete. (Starvation does not imply failure.)
        // Lookup termination (as defined in isLookupTermination) is not possible in small networks.
        // Starvation is a successful query termination in small networks.
        if (!(isLookupTermination() || isStarvationTermination())) {
            completed = false;
        }

        // extract the top K not unreachable peers


        List<QueryPeerState> qp = queryPeers.GetClosestNInStates(dht.bucketSize,
                Arrays.asList(PeerState.PeerHeard, PeerState.PeerWaiting, PeerState.PeerQueried));

        LookupWithFollowupResult res = new LookupWithFollowupResult();
        for (QueryPeerState p : qp) {
            res.peers.put(p.id, p.state);
        }
        res.completed = completed;

        // get the top K overall peers
        /* TODO target
        sortedPeers = kb.SortClosestPeers(peers, target);
        if len(sortedPeers) > q.dht.bucketSize {
            sortedPeers = sortedPeers[:q.dht.bucketSize]
        }

        // return the top K not unreachable peers as well as their states at the end of the query

        res := &lookupWithFollowupResult{
            peers:     sortedPeers,
                    state:     make([]qpeerset.PeerState, len(sortedPeers)),
            completed: completed,
        }

        for i, p := range sortedPeers {
            res.state[i] = peerState[p]
        }*/

        return res;


    }


    private void updateState(@NonNull Closeable ctx, @NonNull QueryUpdate up) {
        if (terminated) {
            throw new RuntimeException("update should not be invoked after the logical lookup termination");
        }
        /* TODO
        PublishLookupEvent(ctx,
                NewLookupEvent(
                        q.dht.self,
                        q.id,
                        q.key,
                        nil,
                        NewLookupUpdateEvent(
                                up.cause,
                                up.cause,
                                up.heard,       // heard
                                nil,            // waiting
                                up.queried,     // queried
                                up.unreachable, // unreachable
                        ),
                        nil,
                        ),
                )*/
        for (PeerId p : up.heard) {
            if (Objects.equals(p, dht.self)) { // don't add self.
                continue;
            }
            queryPeers.TryAdd(p, up.getCause());
        }


        for (PeerId p : up.queried) {
            if (Objects.equals(p, dht.self)) { // don't add self.
                continue;
            }
            PeerState st = queryPeers.GetState(p);
            if (st == PeerState.PeerWaiting) {
                queryPeers.SetState(p, PeerState.PeerQueried);
                peerTimes.put(p, up.queryDuration);
            } else {
                throw new RuntimeException("kademlia protocol error: tried to transition to " +
                        "the queried state from state " + st.toString());
            }
        }
        for (PeerId p : up.unreachable) {
            if (Objects.equals(p, dht.self)) { // don't add self.
                continue;
            }
            PeerState st = queryPeers.GetState(p);
            if (st == PeerState.PeerWaiting) {
                queryPeers.SetState(p, PeerState.PeerUnreachable);
            } else {
                throw new RuntimeException("kademlia protocol error: tried to transition to the " +
                        "unreachable state from state " + st.toString());
            }
        }
    }

    public void run(Closeable ctx) throws ClosedException, InterruptedException {

        int alpha = dht.alpha;


        BlockingQueue<QueryUpdate> queue = new ArrayBlockingQueue<>(alpha);

        QueryUpdate update = new QueryUpdate(dht.self);
        update.heard.addAll(seedPeers);
        queue.offer(update);

        while (true) {

            QueryUpdate current = queue.take();

            updateState(ctx, current);
            PeerId cause = current.getCause();


            // calculate the maximum number of queries we could be spawning.
            // Note: NumWaiting will be updated in spawnQuery
            int maxNumQueriesToSpawn = alpha - queryPeers.NumWaiting();

            // termination is triggered on end-of-lookup conditions or starvation of unused peers
            // it also returns the peers we should query next for a maximum of `maxNumQueriesToSpawn` peers.
            Pair<Boolean, List<PeerId>> result = isReadyToTerminate(ctx, maxNumQueriesToSpawn);

            if (result.first) {
                terminate(ctx);
            }

            if (terminated) {
                return;
            }

            // try spawning the queries, if there are no available peers to query then we won't spawn them
            for (PeerId p : result.second) {
                spawnQuery(ctx, queue, cause, p);
            }

        }

    }


    // spawnQuery starts one query, if an available heard peer is found
    private void spawnQuery(@NonNull Closeable ctx, @NonNull BlockingQueue<QueryUpdate> queue,
                            @NonNull PeerId cause, @NonNull PeerId queryPeer) {
        /*
        PublishLookupEvent(ctx,
                NewLookupEvent(
                        q.dht.self,
                        q.id,
                        q.key,
                        NewLookupUpdateEvent(
                                cause,
                                q.queryPeers.GetReferrer(queryPeer),
                                nil,                  // heard
                                []peer.ID{queryPeer}, // waiting
        nil,                  // queried
                nil,                  // unreachable
			),
        nil,
                nil,
		),
	)*/
        queryPeers.SetState(queryPeer, PeerState.PeerWaiting);


        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                queryPeer(ctx, queue, queryPeer);
            } catch (ClosedException ignore) {
                queue.clear();
                queue.offer(new QueryUpdate(queryPeer));
                // nothing to do here (works as expected)
            } catch (Throwable throwable) {
                // not expected exception
                LogUtils.error(TAG, throwable);
            }
        });
    }


    // queryPeer queries a single peer and reports its findings on the channel.
    // queryPeer does not access the query state in queryPeers!
    private void queryPeer(@NonNull Closeable ctx, @NonNull BlockingQueue<QueryUpdate> queue,
                           @NonNull PeerId queryPeer) throws ClosedException {


        long startQuery = System.currentTimeMillis();

        if (ctx.isClosed()) {
            throw new ClosedException();
        }
        try {

            Collection<Multiaddr> collections = dht.host.getAddressBook().getAddrs(queryPeer).get();
            if (collections != null) {
                dht.host.getNetwork().connect(queryPeer,
                        Iterables.toArray(collections, Multiaddr.class)).get(
                        IPFS.TIMEOUT_DHT_PEER, TimeUnit.SECONDS
                );
            } else {
                dht.host.getNetwork().connect(queryPeer).get(
                        IPFS.TIMEOUT_DHT_PEER, TimeUnit.SECONDS);
            }

        } catch (Throwable throwable) {
            if (ctx.isClosed()) {
                throw new ClosedException();
            }

            // remove the peer if there was a dial failure..but not because of a context cancellation
            // TODO LogUtils.error(TAG, throwable);
            dht.peerStoppedDHT(queryPeer); // TODO
            try {
                Collection<Multiaddr> collections = dht.host.getAddressBook().getAddrs(queryPeer).get();
                if (collections != null) {
                    LogUtils.error(TAG, collections.toString());
                }
            } catch (Throwable ignore){
                //
            }

            QueryUpdate update = new QueryUpdate(queryPeer);
            update.unreachable.add(queryPeer);
            queue.offer(update);
            return;
        }


        try {
            // send query RPC to the remote peer
            List<AddrInfo> newPeers = queryFn.func(ctx, queryPeer);

            if (ctx.isClosed()) {
                throw new ClosedException();
            }

            long queryDuration = startQuery - System.currentTimeMillis();

            // query successful, try to add to RT
            dht.peerFound(queryPeer, true);

            // process new peers
            List<PeerId> saw = new ArrayList<>();
            for (AddrInfo next : newPeers) {
                if (Objects.equals(next.getPeerId(), dht.self)) { // don't add self.
                    LogUtils.error(TAG, "PEERS CLOSER -- worker for: found self " + queryPeer.toBase58());
                    continue;
                }

                saw.add(next.getPeerId());
            }

            QueryUpdate update = new QueryUpdate(queryPeer);
            update.heard.addAll(saw);
            update.queried.add(queryPeer);
            update.queryDuration = queryDuration;
            queue.offer(update);
            // TODO  ch <- &queryUpdate{cause: p, heard: saw, queried: []peer.ID{p}, queryDuration: queryDuration}

        } catch (ClosedException closedException) {
            throw closedException;
        } catch (ProtocolNotSupported | ConnectionFailure ignore) {
            QueryUpdate update = new QueryUpdate(queryPeer);
            update.unreachable.add(queryPeer);
            queue.offer(update);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            /* TODO
            if queryCtx.Err() == nil {
                q.dht.peerStoppedDHT(q.dht.ctx, p)
            }*/
            QueryUpdate update = new QueryUpdate(queryPeer);
            update.unreachable.add(queryPeer);
            queue.offer(update);
            // TODO ch <- &queryUpdate{cause: p, unreachable: []peer.ID{p}}
        }


    }

    private void terminate(Closeable ctx) throws ClosedException {
        if (terminated) {
            return;
        }
        if (ctx.isClosed()) {
            throw new ClosedException();
        }
        /* TODO
        PublishLookupEvent(ctx,
                NewLookupEvent(
                        q.dht.self,
                        q.id,
                        q.key,
                        nil,
                        nil,
                        NewLookupTerminateEvent(reason),
                        ),
                )
        cancel() // abort outstanding queries

         */
        terminated = true;
    }


    // From the set of all nodes that are not unreachable,
    // if the closest beta nodes are all queried, the lookup can terminate.
    private boolean isLookupTermination() {
        List<QueryPeerState> peers = queryPeers.GetClosestNInStates(dht.beta, Arrays.asList(
                PeerState.PeerHeard, PeerState.PeerWaiting, PeerState.PeerQueried));
        for (QueryPeerState qps : peers) {
            if (queryPeers.GetState(qps.id) != PeerState.PeerQueried) {
                return false;
            }
        }
        return true;
    }

    private boolean isStarvationTermination() {
        return queryPeers.NumHeard() == 0 && queryPeers.NumWaiting() == 0;
    }


    private Pair<Boolean, List<PeerId>> isReadyToTerminate(@NonNull Closeable ctx, int nPeersToQuery) {
        // give the application logic a chance to terminate
        if (stopFn.func()) {
            return Pair.create(true, Collections.emptyList());
        }
        if (isStarvationTermination()) {
            return Pair.create(true, Collections.emptyList());
        }
        if (isLookupTermination()) {
            return Pair.create(true, Collections.emptyList());
        }
        if (ctx.isClosed()) {
            return Pair.create(true, Collections.emptyList());
        }

        // The peers we query next should be ones that we have only Heard about.
        List<PeerId> peersToQuery = new ArrayList<>();
        List<QueryPeerState> peers = queryPeers.GetClosestInStates(Collections.singletonList(PeerState.PeerHeard));
        int count = 0;
        for (QueryPeerState p : peers) {
            peersToQuery.add(p.id);
            count++;
            if (count == nPeersToQuery) {
                break;
            }
        }

        return Pair.create(false, peersToQuery);
    }


    private void recordPeerIsValuable(@NonNull PeerId p) {
        dht.routingTable.UpdateLastUsefulAt(p, System.currentTimeMillis());
    }

    public void recordValuablePeers() {
        // Valuable peers algorithm:
        // Label the seed peer that responded to a query in the shortest amount of time as the "most valuable peer" (MVP)
        // Each seed peer that responded to a query within some range (i.e. 2x) of the MVP's time is a valuable peer
        // Mark the MVP and all the other valuable peers as valuable
        long mvpDuration = Long.MAX_VALUE;

        for (PeerId p : seedPeers) {
            Long queryTime = peerTimes.get(p);
            if (queryTime != null) {
                if (queryTime < mvpDuration) {
                    mvpDuration = queryTime;
                }
            }
        }
        for (PeerId p : seedPeers) {
            Long queryTime = peerTimes.get(p);
            if (queryTime != null) {
                if (queryTime < (mvpDuration * 2)) {
                    recordPeerIsValuable(p);
                }
            }
        }
    }
}
