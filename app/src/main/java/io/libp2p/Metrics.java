package io.libp2p;

import androidx.annotation.NonNull;

import java.util.concurrent.ConcurrentHashMap;

import io.libp2p.core.PeerId;

public class Metrics extends ConcurrentHashMap<PeerId, Long> {


    public long getLatency(@NonNull PeerId peerId) {
        Long duration = get(peerId);
        if (duration != null) {
            return duration;
        }
        return Long.MAX_VALUE;
    }

    public void addLatency(@NonNull PeerId peerId, long latency) {
        put(peerId, latency);
    }
}
