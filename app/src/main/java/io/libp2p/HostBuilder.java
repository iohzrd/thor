package io.libp2p;

import androidx.annotation.NonNull;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import io.LogUtils;
import io.libp2p.core.Host;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.dsl.Builder;
import io.libp2p.core.dsl.BuilderJKt;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.mux.StreamMuxerProtocol;
import io.libp2p.core.security.SecureChannel;
import io.libp2p.core.transport.Transport;
import io.libp2p.transport.ConnectionUpgrader;

public class HostBuilder {
    private static final String TAG = HostBuilder.class.getSimpleName();

    public HostBuilder() {
        this(DefaultMode.Standard);
    }

    public HostBuilder(DefaultMode defaultMode) {
        defaultMode_ = defaultMode;
    }

    public enum DefaultMode {
        None,
        Standard;

        private Builder.Defaults asBuilderDefault() {
            if (this.equals(None)) {
                return Builder.Defaults.None;
            }
            return Builder.Defaults.Standard;
        }
    };

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

    private DefaultMode defaultMode_;
    private List<Function<ConnectionUpgrader, Transport>> transports_ = new ArrayList<>();
    private List<Function<PrivKey, SecureChannel>> secureChannels_ = new ArrayList<>();
    private List<Supplier<StreamMuxerProtocol>> muxers_ = new ArrayList<>();
    private List<ProtocolBinding<?>> protocols_ = new ArrayList<>();
    private List<String> listenAddresses_ = new ArrayList<>();
    private PrivKey privKey;


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
}
