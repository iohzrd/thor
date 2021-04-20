package io.libp2p;

import androidx.annotation.NonNull;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.LogUtils;
import io.libp2p.core.Connection;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;

public class ConnectionManager {
    private static final String TAG = ConnectionManager.class.getSimpleName();
    @NonNull
    private final Host host;
    private final Metrics metrics = new Metrics();
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

    @NonNull
    public Metrics getMetrics() {
        return metrics;
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

    public void trimConnections() {
        for (Connection connection : host.getNetwork().getConnections()) {

            try {
                PeerId peerId = connection.secureSession().getRemoteId();
                if (!isProtected(peerId)) {
                    connection.close().get(gracePeriod, TimeUnit.SECONDS);
                }

            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }

        }
    }
}
