package io.dht;

import android.util.Pair;

import androidx.annotation.NonNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.Closeable;
import io.ipfs.ClosedException;
import io.libp2p.core.PeerId;

public class Query {

    private final UUID id;

    // target key for the lookup
    private final String key;

    // the query context.
    // ctx context.Context

    private final KadDHT dht;

    // seedPeers is the set of peers that seed the query
    private final List<PeerId> seedPeers;

    // peerTimes contains the duration of each successful query to a peer
    private final ConcurrentHashMap<PeerId, Duration> peerTimes = new ConcurrentHashMap<>();

    // queryPeers is the set of peers known by this query and their respective states.
    private final QueryPeerSet queryPeers;
    // the function that will be used to query a single peer.
    private final QueryFunc queryFn;

    // waitGroup ensures lookup does not end until all query goroutines complete.
    // TODO waitGroup sync.WaitGroup
    // stopFn is used to determine if we should stop the WHOLE disjoint query.
    private final StopFunc stopFn;
    // terminated is set when the first worker thread encounters the termination condition.
    // Its role is to make sure that once termination is determined, it is sticky.
    boolean terminated = false;

    public Query(@NonNull KadDHT dht, @NonNull UUID uuid, @NonNull String key,
                 @NonNull List<PeerId> seedPeers, @NonNull QueryPeerSet queryPeers,
                 @NonNull QueryFunc queryFn, @NonNull StopFunc stopFn) {
        this.id = uuid;
        this.key = key;
        this.dht = dht;
        this.seedPeers = seedPeers;
        this.queryPeers = queryPeers;
        this.queryFn = queryFn;
        this.stopFn = stopFn;
    }


    public LookupWithFollowupResult constructLookupResult(ID targetKadID) {
        return null; // TODO
    }


    private void updateState(Closeable ctx, QueryUpdate up) {
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
            queryPeers.TryAdd(p, up.cause);
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

    public void run(Closeable ctx) throws ClosedException {

        int alpha = dht.alpha; // TODO set alpha


        ExecutorService executor = Executors.newFixedThreadPool(alpha);


        QueryUpdate update = new QueryUpdate(dht.self, seedPeers);
        //ch := make(chan *queryUpdate, alpha)
        //ch <- &queryUpdate{cause: q.dht.self, heard: q.seedPeers}

        // return only once all outstanding queries have completed.
        //defer q.waitGroup.Wait()
        while (true) {

            updateState(ctx, update);
            PeerId cause = update.cause;


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
                spawnQuery(ctx, cause, p);
            }
        }
        // TODO
    }


    // spawnQuery starts one query, if an available heard peer is found
    private void spawnQuery(@NonNull Closeable ctx, PeerId cause, PeerId queryPeer) {
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
        // TODO q.waitGroup.Add(1)
        // TODO  go q.queryPeer(ctx, ch, queryPeer)
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


    private Pair<Boolean, List<PeerId>> isReadyToTerminate(Closeable ctx, int nPeersToQuery) {
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

    public void recordValuablePeers() {
        // TODO
    }
}
