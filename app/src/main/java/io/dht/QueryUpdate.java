package io.dht;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.libp2p.core.PeerId;

public class QueryUpdate {
    private final PeerId cause;
    public List<PeerId> queried = new ArrayList<>();
    public List<PeerId> heard = new ArrayList<>();
    public List<PeerId> unreachable = new ArrayList<>();
    long queryDuration;

    public QueryUpdate(@NonNull PeerId cause) {
        this.cause = cause;
    }

    public PeerId getCause() {
        return cause;
    }
}
