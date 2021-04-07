package io.dht;

import androidx.annotation.NonNull;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.Closeable;
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
    private final QueryPeerset queryPeers;
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
                 @NonNull List<PeerId> seedPeers, @NonNull QueryPeerset queryPeers,
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

    public void run(Closeable ctx) {
    }

    public void recordValuablePeers() {
    }
}
