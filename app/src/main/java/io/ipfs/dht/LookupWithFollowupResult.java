package io.ipfs.dht;

import java.util.concurrent.ConcurrentHashMap;

import io.ipfs.host.PeerId;

public class LookupWithFollowupResult {
    final ConcurrentHashMap<PeerId, PeerState> peers = new ConcurrentHashMap<>();
    // the top K not unreachable peers at the end of the query
    // the peer states at the end of the query

    // indicates that neither the lookup nor the followup has been prematurely terminated by an external condition such
    // as context cancellation or the stop function being called.
    boolean completed;

}
