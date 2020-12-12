package threads.thor.bt;

import androidx.annotation.NonNull;

import java.net.InetAddress;
import java.util.function.Predicate;

import threads.thor.bt.kad.DHT;
import threads.thor.bt.net.PeerId;

public interface DHTConfiguration {

    @NonNull
    PeerId getLocalPeerId();

    int getListeningPort();

    /**
     * if true then no attempt to bootstrap through well-known nodes is made.
     * you either must have a persisted routing table which can be loaded or
     * manually seed the routing table by calling {@link DHT#addDHTNode(String, int)}
     */
    boolean noRouterBootstrap();

    /**
     * If true one DHT node per globally routable unicast address will be used. Recommended for IPv6 nodes or servers-class machines directly connected to the internet.<br>
     * If false only one node will be bound. Usually to the the default route. Recommended for IPv4 nodes.
     */
    boolean allowMultiHoming();

    /**
     * A DHT node will automatically select socket bind addresses based on internal policies from available addresses,
     * the predicate can be used to limit this selection to a subset.
     * <p>
     * A predicate that allows the <em>any local address</em> of a particular address family is considered to allow all addresses <em>of that family</em>
     * <p>
     * The default implementation does not apply any restrictions.
     * <p>
     * The predicate may be be evaluated frequently, implementations should be approximately constant-time.
     */
    default Predicate<InetAddress> filterBindAddress() {
        return (unused) -> true;
    }
}
