package io.dht;

import androidx.annotation.NonNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.libp2p.core.PeerId;

public class QueryUpdate {
    public final PeerId cause;
    public List<PeerId> heard = new ArrayList<>();
    List<PeerId> queried = new ArrayList<>();
    List<PeerId> unreachable = new ArrayList<>();

    Duration queryDuration;

    public QueryUpdate(@NonNull PeerId cause, @NonNull List<PeerId> peers) {
        this.cause = cause;
        this.heard.addAll((peers));
    }
}
