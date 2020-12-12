/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package threads.thor.bt.net;

import java.net.InetAddress;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import threads.thor.bt.peer.PeerOptions;

/**
 * @since 1.0
 */
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

    public static InetPeer build(InetAddress address) {
        return builder(address).build();
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
