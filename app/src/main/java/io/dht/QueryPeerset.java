package io.dht;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class QueryPeerset {
    private final QueryKey key;
    private final List<QueryPeerState> all = new ArrayList<>();
    boolean sorted;

    private QueryPeerset(@NonNull QueryKey key) {
        this.key = key;
        this.sorted = false;
    }

// NewQueryPeerset creates a new empty set of peers.
// key is the target key of the lookup that this peer set is for.

    public static QueryPeerset create(String key) {
        return null; //new QueryPeerset(ks.XORKeySpace.Key(key.getBytes()); // TODO
    }
}
