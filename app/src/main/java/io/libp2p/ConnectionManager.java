package io.libp2p;

import androidx.annotation.NonNull;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.LogUtils;
import io.libp2p.core.Connection;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;

public class ConnectionManager implements Metrics {
    private static final String TAG = ConnectionManager.class.getSimpleName();
    @NonNull
    private final Host host;
    private final ConcurrentHashMap<PeerId, Long> metrics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PeerId, Long> actives = new ConcurrentHashMap<>();
    private final Set<PeerId> tags = ConcurrentHashMap.newKeySet();
    private final int lowWater;
    private final int highWater;
    private final int gracePeriod;


    public ConnectionManager(@NonNull Host host, int lowWater, int highWater, int gracePeriod) {
        this.host = host;
        this.lowWater = lowWater;
        this.highWater = highWater;
        this.gracePeriod = gracePeriod;
    }

    public long getLatency(@NonNull PeerId peerId) {
        Long duration = metrics.get(peerId);
        if (duration != null) {
            return duration;
        }
        return Long.MAX_VALUE;
    }

    public void addLatency(@NonNull PeerId peerId, long latency) {
        metrics.put(peerId, latency);
    }


    public void protectPeer(@NonNull PeerId peerId) {
        tags.add(peerId);
    }

    public void unprotectPeer(@NonNull PeerId peerId) {
        tags.remove(peerId);
    }

    public boolean isProtected(@NonNull PeerId peerId) {
        return tags.contains(peerId);
    }

    @Override
    public void active(@NonNull PeerId peerId) {
        actives.put(peerId, System.currentTimeMillis());
    }

    @Override
    public void done(@NonNull PeerId peerId) {
        actives.remove(peerId);
    }

    private boolean isActive(@NonNull PeerId peerId){
        Long time = actives.get(peerId);
        if(time != null){
            return (System.currentTimeMillis() - time) <  (gracePeriod * 1000);
        }
        return false;
    }


    public int numConnections(){
        return host.getNetwork().getConnections().size();
    }

    public void trimConnections() {

        int connections = numConnections();
        LogUtils.verbose(TAG, "numConnections (before) " + connections);

        if(connections > highWater) {

            int hasToBeClosed = connections - lowWater;

            // TODO maybe sort connections how fast they are (the fastest will not be closed)

            for (Connection connection : host.getNetwork().getConnections()) {
                if(hasToBeClosed > 0) {
                    try {
                        PeerId peerId = connection.secureSession().getRemoteId();
                        if (!isProtected(peerId) && !isActive(peerId)) {
                            connection.close().get();
                            hasToBeClosed--;
                        }
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                }
            }
        }
        LogUtils.verbose(TAG, "numConnections (after) " + numConnections());
    }
}