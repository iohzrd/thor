package io.libp2p;

import androidx.annotation.NonNull;

import com.google.common.collect.Iterables;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;

import io.LogUtils;
import io.core.Closeable;
import io.core.ClosedException;
import io.core.ConnectionIssue;
import io.ipfs.DnsResolver;
import io.libp2p.core.Connection;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.dsl.Builder;
import io.libp2p.core.dsl.BuilderJKt;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multiformats.Protocol;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.mux.StreamMuxerProtocol;
import io.libp2p.core.security.SecureChannel;
import io.libp2p.core.transport.Transport;
import io.libp2p.transport.ConnectionUpgrader;

public class HostBuilder {
    private static final String TAG = HostBuilder.class.getSimpleName();
    private final DefaultMode defaultMode_;
    private final List<Function<ConnectionUpgrader, Transport>> transports_ = new ArrayList<>();
    private final List<Function<PrivKey, SecureChannel>> secureChannels_ = new ArrayList<>();

    private final List<Supplier<StreamMuxerProtocol>> muxers_ = new ArrayList<>();
    private final List<ProtocolBinding<?>> protocols_ = new ArrayList<>();
    private final List<String> listenAddresses_ = new ArrayList<>();
    private PrivKey privKey;

    public HostBuilder() {
        this(DefaultMode.Standard);
    }

    public HostBuilder(DefaultMode defaultMode) {
        defaultMode_ = defaultMode;
    }

    // TODO not really working
    @NonNull
    public static String getLocalIpAddress() { // TODO IPv6
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (inetAddress instanceof Inet4Address) {
                        if (!inetAddress.isLoopbackAddress()) {
                            return inetAddress.getHostAddress();
                        }
                    }
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return "127.0.0.1"; // TODO this is wrong for IPv6
    }

    public static boolean isConnected(@NonNull Host host, @NonNull PeerId peerId) {
        try {
            return host.getNetwork().connect(peerId).get() != null;
        } catch (Throwable throwable) {
            return false;
        }
    }

    public static Object stream(@NonNull Closeable closeable, @NonNull Host host,
                                @NonNull String protocol, @NonNull Connection con)
            throws ClosedException, ExecutionException, InterruptedException {


        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        CompletableFuture<Object> ctrl = host.newStream(
                Collections.singletonList(protocol), con).getController();


        while (!ctrl.isDone()) {
            if (closeable.isClosed()) {
                ctrl.cancel(true);
            }
        }

        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        return ctrl.get();
    }

    private static List<Multiaddr> prepareAddresses(@NonNull Host host, @NonNull PeerId peerId) {
        List<Multiaddr> all = new ArrayList<>();
        for (Multiaddr ma : getAddresses(host, peerId)) {
            try {
                if (ma.has(Protocol.DNS6)) {
                    all.add(DnsResolver.resolveDns6(ma));
                } else if (ma.has(Protocol.DNS4)) {
                    all.add(DnsResolver.resolveDns4(ma));
                } else if (ma.has(Protocol.DNSADDR)) {
                    all.addAll(DnsResolver.resolveDnsAddress(ma));
                } else {
                    all.add(ma);
                }
            } catch (Throwable throwable) {
                LogUtils.verbose(TAG, throwable.getClass().getSimpleName());
            }
        }

        /*  todo seperate them by protocol QUIC when supported and rest
        all.sort((o1, o2) -> {

            // TODO better sorting
            int result = Boolean.compare(o1.has(Protocol.QUIC), o2.has(Protocol.QUIC));
            if (result == 0) {
                result = Boolean.compare(o1.has(Protocol.TCP), o2.has(Protocol.TCP));
            }
            return result;
        });*/
        return all;
    }

    public static Connection connect(@NonNull Closeable closeable, @NonNull Host host,
                                     @NonNull PeerId peerId) throws ClosedException, ConnectionIssue {

        try {
            if (closeable.isClosed()) {
                throw new ClosedException();
            }
            List<Multiaddr> addrInfo = prepareAddresses(host, peerId);

            if (!addrInfo.isEmpty()) {

                CompletableFuture<Connection> future = host.getNetwork().connect(peerId,
                        Iterables.toArray(addrInfo, Multiaddr.class));

                while (!future.isDone()) {
                    if (closeable.isClosed()) {
                        future.cancel(true);
                    }
                }
                if (closeable.isClosed()) {
                    throw new ClosedException();
                }

                return future.get();

            } else {
                return host.getNetwork().connect(peerId).get();
            }
        } catch (ClosedException closedException) {
            throw closedException;
        } catch (Throwable throwable) {
            throw new ConnectionIssue();
        }


    }

    public static void addAddrs(@NonNull Host host, @NonNull AddrInfo addrInfo) {

        try {
            PeerId peerId = addrInfo.getPeerId();
            Collection<Multiaddr> info = host.getAddressBook().getAddrs(peerId).get();

            if (info != null) {
                host.getAddressBook().addAddrs(peerId, Long.MAX_VALUE, addrInfo.getAddresses());
            } else {
                host.getAddressBook().setAddrs(peerId, Long.MAX_VALUE, addrInfo.getAddresses());
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public static Set<Multiaddr> getAddresses(@NonNull Host host, @NonNull PeerId peerId) {
        Set<Multiaddr> all = new HashSet<>();
        try {
            Collection<Multiaddr> addrInfo = host.getAddressBook().get(peerId).get();
            if (addrInfo != null) {
                all.addAll(addrInfo);
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return all;
    }

    private static void removeMultiaddress(@NonNull Host host, @NonNull PeerId peerId, @NonNull Multiaddr addr) {
        try {
            host.getAddressBook().get(peerId).get().remove(addr);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    // TODO should be improved with information of the device real
    // public IP, probably asks other devices for getting
    // the real IP address (relay, punch-hole, etc stuff
    @NonNull
    public static List<Multiaddr> listenAddresses(@NonNull Host host) {
        try {
            // TODO the listen address does not contain real IP address

            List<Multiaddr> list = new ArrayList<>();
            List<Transport> transports = host.getNetwork().getTransports();
            for (Transport transport : transports) {
                list.addAll(transport.listenAddresses());
            }
            return list;
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return host.listenAddresses();

    }

    @SafeVarargs
    public final HostBuilder transport(
            Function<ConnectionUpgrader, Transport>... transports) {
        transports_.addAll(Arrays.asList(transports));
        return this;
    }

    @SafeVarargs
    public final HostBuilder secureChannel(
            Function<PrivKey, SecureChannel>... secureChannels) {
        secureChannels_.addAll(Arrays.asList(secureChannels));
        return this;
    }

    @SafeVarargs
    public final HostBuilder muxer(
            Supplier<StreamMuxerProtocol>... muxers) {
        muxers_.addAll(Arrays.asList(muxers));
        return this;
    }

    public final HostBuilder protocol(
            ProtocolBinding<?>... protocols) {
        protocols_.addAll(Arrays.asList(protocols));
        return this;
    }

    public final HostBuilder listen(
            String... addresses) {
        listenAddresses_.addAll(Arrays.asList(addresses));
        return this;
    }

    public final HostBuilder identity(
            PrivKey privKey) {
        this.privKey = privKey;
        return this;
    }

    public Host build() {
        return BuilderJKt.hostJ(
                defaultMode_.asBuilderDefault(),
                b -> {
                    b.getIdentity().setFactory(() -> privKey);

                    transports_.forEach(t ->
                            b.getTransports().add(t::apply)
                    );
                    secureChannels_.forEach(sc ->
                            b.getSecureChannels().add(sc::apply)
                    );
                    muxers_.forEach(m ->
                            b.getMuxers().add(m.get())
                    );
                    b.getProtocols().addAll(protocols_);
                    listenAddresses_.forEach(a ->
                            b.getNetwork().listen(a)
                    );
                }
        );
    } // build

    public enum DefaultMode {
        None,
        Standard;

        private Builder.Defaults asBuilderDefault() {
            if (this.equals(None)) {
                return Builder.Defaults.None;
            }
            return Builder.Defaults.Standard;
        }
    }
}
