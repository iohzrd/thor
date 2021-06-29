package threads.lite.host;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import threads.lite.cid.Multiaddr;
import threads.lite.cid.PeerId;

public class PeerInfo {
    @NonNull
    private final PeerId peerId;
    @NonNull
    private final String agent;

    @NonNull
    private final String version;
    @NonNull
    private final List<Multiaddr> addresses;
    @NonNull
    private final List<String> protocols;
    @Nullable
    private final Multiaddr observed;

    public PeerInfo(@NonNull PeerId peerId,
                    @NonNull String agent,
                    @NonNull String version,
                    @NonNull List<Multiaddr> addresses,
                    @NonNull List<String> protocols,
                    @Nullable Multiaddr observed) {
        this.peerId = peerId;
        this.agent = agent;
        this.version = version;
        this.addresses = addresses;
        this.protocols = protocols;
        this.observed = observed;
    }

    @NonNull
    public String getVersion() {
        return version;
    }

    @NonNull
    public List<String> getProtocols() {
        return protocols;
    }

    @Nullable
    public Multiaddr getObserved() {
        return observed;
    }

    @NonNull
    public List<Multiaddr> getAddresses() {
        return addresses;
    }

    @NonNull
    @Override
    public String toString() {
        return "PeerInfo{" +
                "peerId=" + peerId +
                ", agent='" + agent + '\'' +
                ", version='" + version + '\'' +
                ", addresses=" + addresses +
                ", protocols=" + protocols +
                ", observed=" + observed +
                '}';
    }

    @NonNull
    public PeerId getPeerId() {
        return peerId;
    }

    @NonNull
    public String getAgent() {
        return agent;
    }

}
