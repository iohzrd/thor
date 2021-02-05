package threads.thor.magnet.net;

import java.net.InetAddress;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import threads.thor.magnet.peer.PeerOptions;

public class InetPeer implements Peer {

    private static final int UNKNOWN_PORT = -1;

    private final Supplier<InetAddress> addressSupplier;
    private final PeerId peerId;
    private final PeerOptions options;
    private volatile int port;

    private InetPeer(Supplier<InetAddress> addressSupplier, int port, PeerId peerId, PeerOptions options) {
        this.addressSupplier = addressSupplier;
        this.port = port;
        this.peerId = peerId;
        this.options = options;
    }

    private static void checkPort(int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
    }

    private static Builder builder(InetPeerAddress holder) {
        int port = holder.getPort();
        checkPort(port);
        return new Builder(holder::getAddress, port);
    }

    public static Builder builder(InetAddress address, int port) {
        checkPort(port);
        return new Builder(() -> address, port);
    }

    public static Builder builder(InetAddress address) {
        return new Builder(() -> address, UNKNOWN_PORT);
    }

    public static InetPeer build(InetPeerAddress peerAddress) {
        return builder(peerAddress).build();
    }

    public static InetPeer build(InetAddress address, int port) {
        return builder(address, port).build();
    }

    @Override
    public InetAddress getInetAddress() {
        return addressSupplier.get();
    }

    @Override
    public boolean isPortUnknown() {
        return (port == UNKNOWN_PORT);
    }

    @Override
    public int getPort() {
        return port;
    }

    public void setPort(int newPort) {
        checkPort(newPort);
        if (port != UNKNOWN_PORT && port != newPort) {
            throw new IllegalStateException("Port already set to: " + port + "." +
                    " Attempted to update to: " + newPort);
        }
        port = newPort;
    }

    @Override
    public Optional<PeerId> getPeerId() {
        return Optional.ofNullable(peerId);
    }

    @Override
    public PeerOptions getOptions() {
        return options;
    }

    public static class Builder {

        private final Supplier<InetAddress> addressSupplier;
        private final int port;
        private PeerId peerId;
        private PeerOptions options;

        private Builder(Supplier<InetAddress> addressSupplier, int port) {
            this.addressSupplier = addressSupplier;
            this.port = port;
        }

        public Builder peerId(PeerId peerId) {
            this.peerId = Objects.requireNonNull(peerId);
            return this;
        }

        public Builder options(PeerOptions options) {
            this.options = Objects.requireNonNull(options);
            return this;
        }

        public InetPeer build() {
            PeerOptions options = (this.options == null) ?
                    PeerOptions.defaultOptions() : this.options;
            return new InetPeer(addressSupplier, port, peerId, options);
        }
    }
}
