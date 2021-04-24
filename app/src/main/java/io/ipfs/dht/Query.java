package io.ipfs.dht;

import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import io.LogUtils;
import io.core.Closeable;
import io.core.ClosedException;
import io.core.ConnectionIssue;
import io.core.ProtocolIssue;
import io.core.TimeoutIssue;
import io.libp2p.AddrInfo;
import io.libp2p.core.PeerId;

public class Query {

    private static final String TAG = Query.class.getSimpleName();

    @NonNull
    private final KadDHT dht;
    @NonNull
    private final List<PeerId> seedPeers;
    @NonNull
    private final QueryPeerSet queryPeers;
    @NonNull
    private final KadDHT.QueryFunc queryFn;
    @NonNull
    private final KadDHT.StopFunc stopFn;
    @NonNull
    private final BlockingQueue<QueryUpdate> queue;
    private final int alpha;

    public Query(@NonNull KadDHT dht, @NonNull byte[] key, @NonNull List<PeerId> seedPeers,
                 @NonNull KadDHT.QueryFunc queryFn, @NonNull KadDHT.StopFunc stopFn) {
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
        List<PeerId> peers = new ArrayList<>();
        Map<PeerId, PeerState> map = new HashMap<>();
        for (QueryPeerState p : qp) {
            peers.add(p.id);
            map.put(p.id, p.getState());
        }
        res.completed = completed;

        PeerDistanceSorter pds = new PeerDistanceSorter(target);
        for (PeerId p : peers) {
            pds.appendPeer(p, Util.ConvertPeerID(p));
        }

        List<PeerId> sorted = pds.sortedList();

        for (PeerId peerId : sorted) {
            PeerState peerState = map.get(peerId);
            Objects.requireNonNull(peerState);
            res.peers.put(peerId, peerState);
        }

        return res;
    }


    private void updateState(@NonNull QueryUpdate up) {


        for (PeerId p : up.heard) {
            if (Objects.equals(p, dht.self)) { // don't add self.
                continue;
            }
            queryPeers.TryAdd(p);
        }


        for (PeerId p : up.queried) {
            if (Objects.equals(p, dht.self)) { // don't add self.
                continue;
            }
            PeerState st = queryPeers.GetState(p);
            if (st == PeerState.PeerWaiting) {
                queryPeers.SetState(p, PeerState.PeerQueried);
            } else {
                throw new RuntimeException("internal state");
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
                throw new RuntimeException("internal state");
            }
        }
    }

    public void run(@NonNull Closeable ctx) throws ClosedException, InterruptedException {

        QueryUpdate update = new QueryUpdate();
        update.heard.addAll(seedPeers);
        queue.offer(update);

        while (true) {

            QueryUpdate current = queue.take();

            if (ctx.isClosed()) {
                throw new ClosedException();
            }

            updateState(current);

            // calculate the maximum number of queries we could be spawning.
            // Note: NumWaiting will be updated in spawnQuery
            int maxNumQueriesToSpawn = alpha - queryPeers.NumWaiting();

            // termination is triggered on end-of-lookup conditions or starvation of unused peers
            // it also returns the peers we should query next for a maximum of `maxNumQueriesToSpawn` peers.
            Pair<Boolean, List<PeerId>> result = isReadyToTerminate(maxNumQueriesToSpawn);

            if (!result.first) {

                // try spawning the queries, if there are no available peers to query then we won't spawn them
                for (PeerId queryPeer : result.second) {
                    queryPeers.SetState(queryPeer, PeerState.PeerWaiting);
                    new Thread(() -> {
                        try {
                            queryPeer(ctx, queryPeer);
                        } catch (ClosedException ignore) {
                            queue.clear();
                            queue.offer(new QueryUpdate());
                            // nothing to do here (works as expected)
                        } catch (Throwable throwable) {
                            // not expected exception
                            LogUtils.error(TAG, throwable);
                        }
                    }).start();
                }
            } else {
                LogUtils.warning(TAG, "Termination no succes");
                break;
            }
        }
    }


    private void queryPeer(@NonNull Closeable ctx, @NonNull PeerId queryPeer) throws ClosedException {


        try {

            if (ctx.isClosed()) {
                throw new ClosedException();
            }

            List<AddrInfo> newPeers = queryFn.query(ctx, queryPeer);


            // query successful, try to add to routing table
            dht.peerFound(queryPeer, true);

            // process new peers
            List<PeerId> saw = new ArrayList<>();
            for (AddrInfo next : newPeers) {
                if (Objects.equals(next.getPeerId(), dht.self)) { // don't add self.
                    continue;
                }
                saw.add(next.getPeerId());
            }

            QueryUpdate update = new QueryUpdate();
            update.heard.addAll(saw);
            update.queried.add(queryPeer);
            queue.offer(update);

        } catch (ClosedException closedException) {
            throw closedException;
        } catch (ProtocolIssue | ConnectionIssue ignore) {
            dht.removePeerFromDht(queryPeer);
            QueryUpdate update = new QueryUpdate();
            update.unreachable.add(queryPeer);
            queue.offer(update);
        } catch (TimeoutIssue ignore) {
            QueryUpdate update = new QueryUpdate();
            update.unreachable.add(queryPeer);
            queue.offer(update);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);

            dht.removePeerFromDht(queryPeer);
            QueryUpdate update = new QueryUpdate();
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

    public static class QueryUpdate {

        public List<PeerId> queried = new ArrayList<>();
        public List<PeerId> heard = new ArrayList<>();
        public List<PeerId> unreachable = new ArrayList<>();
    }

}
