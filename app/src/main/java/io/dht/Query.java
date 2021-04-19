package io.dht;

import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import io.LogUtils;
import io.core.Closeable;
import io.core.ClosedException;
import io.core.ConnectionIssue;
import io.core.ProtocolIssue;
import io.libp2p.AddrInfo;
import io.libp2p.core.PeerId;

public class Query {

    private static final String TAG = Query.class.getSimpleName();
    private final UUID id;


    // the query context.
    // ctx context.Context

    private final KadDHT dht;
    private final byte[] key;
    // seedPeers is the set of peers that seed the query
    private final List<PeerId> seedPeers;

    // peerTimes contains the duration of each successful query to a peer
    private final ConcurrentHashMap<PeerId, Long> peerTimes = new ConcurrentHashMap<>();

    // queryPeers is the set of peers known by this query and their respective states.
    private final QueryPeerSet queryPeers;
    // the function that will be used to query a single peer.
    private final KadDHT.QueryFunc queryFn;

    // stopFn is used to determine if we should stop the WHOLE disjoint query.
    private final KadDHT.StopFunc stopFn;

    private final int alpha;
    private final BlockingQueue<QueryUpdate> queue;

    public Query(@NonNull KadDHT dht, @NonNull UUID uuid, @NonNull byte[] key,
                 @NonNull List<PeerId> seedPeers, @NonNull KadDHT.QueryFunc queryFn, @NonNull KadDHT.StopFunc stopFn) {
        this.id = uuid;
        this.key = key; // TODO remove
        this.dht = dht;
        this.seedPeers = seedPeers;
        this.queryPeers = QueryPeerSet.create(key);
        this.queryFn = queryFn;
        this.stopFn = stopFn;
        this.alpha = dht.alpha;
        this.queue = new ArrayBlockingQueue<>(alpha);
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



            res.peers.put(p.id, p.getState());
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



        QueryUpdate update = new QueryUpdate(dht.self);
        update.heard.addAll(seedPeers);
        queue.offer(update);

        while (true) {

            QueryUpdate current = queue.take();

            if (ctx.isClosed()) {
                throw new ClosedException();
            }

            updateState(ctx, current);

            // calculate the maximum number of queries we could be spawning.
            // Note: NumWaiting will be updated in spawnQuery
            int maxNumQueriesToSpawn = alpha - queryPeers.NumWaiting();

            // termination is triggered on end-of-lookup conditions or starvation of unused peers
            // it also returns the peers we should query next for a maximum of `maxNumQueriesToSpawn` peers.
            Pair<Boolean, List<PeerId>> result = isReadyToTerminate(maxNumQueriesToSpawn);

            if (!result.first) {
                // LogUtils.error(TAG, "num " + maxNumQueriesToSpawn + " " + result.second.toString());

                // try spawning the queries, if there are no available peers to query then we won't spawn them
                for (PeerId queryPeer : result.second) {
                    queryPeers.SetState(queryPeer, PeerState.PeerWaiting);


                    new Thread(() -> {
                        try {
                            queryPeer(ctx, queryPeer);
                        } catch (ClosedException ignore) {
                            queue.clear();
                            queue.offer(new QueryUpdate(queryPeer));
                            // nothing to do here (works as expected)
                        } catch (Throwable throwable) {
                            // not expected exception
                            LogUtils.error(TAG, throwable);
                        }
                    }).start();
                }
            } else {
                LogUtils.error(TAG, "Termination no succes");
                break;
            }
        }
    }


    private void queryPeer(@NonNull Closeable ctx, @NonNull PeerId queryPeer) throws ClosedException {


        long startQuery = System.currentTimeMillis();

        try {

            List<AddrInfo> newPeers = queryFn.query(ctx, queryPeer);

            if (ctx.isClosed()) {
                throw new ClosedException();
            }

            long queryDuration = startQuery - System.currentTimeMillis();

            // query successful, try to add to routing table
            dht.peerFound(queryPeer, true, true);

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

        } catch (ClosedException closedException) {
            throw closedException;
        } catch (ProtocolIssue | ConnectionIssue ignore) {
            dht.peerStoppedDHT(queryPeer);
            QueryUpdate update = new QueryUpdate(queryPeer);
            update.unreachable.add(queryPeer);
            queue.offer(update);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable); // TODO print out unknown exceptions

            dht.peerStoppedDHT(queryPeer);
            QueryUpdate update = new QueryUpdate(queryPeer);
            update.unreachable.add(queryPeer);
            queue.offer(update);
        }


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


    private Pair<Boolean, List<PeerId>> isReadyToTerminate(int nPeersToQuery) {


        // give the application logic a chance to terminate
        if (stopFn.stop()) {
            return Pair.create(true, Collections.emptyList());
        }
        if (isStarvationTermination()) {
            return Pair.create(true, Collections.emptyList());
        }
        if (isLookupTermination()) {
            return Pair.create(true, Collections.emptyList());
        }

        // The peers we query next should be ones that we have only Heard about.
        List<PeerId> peersToQuery = new ArrayList<>();
        List<QueryPeerState> peers = queryPeers.GetClosestInStates(
                nPeersToQuery, Collections.singletonList(PeerState.PeerHeard));
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
