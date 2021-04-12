package io.dht;

import java.util.HashSet;

import io.libp2p.core.PeerId;

public class RecordValResult {
    boolean aborted;
    HashSet<PeerId> peersWithBest = new HashSet<>();
    byte[] best;
}
