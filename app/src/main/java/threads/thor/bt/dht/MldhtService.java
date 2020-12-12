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

package threads.thor.bt.dht;


import androidx.annotation.NonNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import threads.LogUtils;
import threads.thor.bt.BtException;
import threads.thor.bt.DHTConfiguration;
import threads.thor.bt.data.DataDescriptor;
import threads.thor.bt.event.EventSource;
import threads.thor.bt.kad.DHT;
import threads.thor.bt.kad.DHT.DHTtype;
import threads.thor.bt.kad.Key;
import threads.thor.bt.kad.PeerAddressDBItem;
import threads.thor.bt.kad.tasks.PeerLookupTask;
import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.InetPeer;
import threads.thor.bt.net.InetPeerAddress;
import threads.thor.bt.net.Peer;
import threads.thor.bt.net.PeerId;
import threads.thor.bt.net.portmapping.PortMapper;
import threads.thor.bt.runtime.Config;
import threads.thor.bt.service.LifecycleBinding;
import threads.thor.bt.service.RuntimeLifecycleBinder;
import threads.thor.bt.torrent.TorrentRegistry;
import threads.thor.bt.utils.NetMask;

import static threads.thor.bt.net.portmapping.PortMapProtocol.UDP;

public class MldhtService implements DHTService {
    private static final String TAG = MldhtService.class.getSimpleName();


    private final Config config;
    private final DHTConfiguration dhtConfig;
    private final DHT dht;
    private final InetAddress localAddress;
    private final boolean useRouterBootstrap;
    private final Collection<InetPeerAddress> publicBootstrapNodes;
    private final Collection<InetPeerAddress> bootstrapNodes;
    private final Set<PortMapper> portMappers;
    private final TorrentRegistry torrentRegistry;

    public MldhtService(@NonNull RuntimeLifecycleBinder lifecycleBinder,
                        @NonNull Config config,
                        @NonNull Set<PortMapper> portMappers,
                        @NonNull TorrentRegistry torrentRegistry,
                        @NonNull EventSource eventSource) {
        this.dht = new DHT(config.shouldUseIPv6() ? DHTtype.IPV6_DHT : DHTtype.IPV4_DHT);
        this.config = config;
        this.dhtConfig = toMldhtConfig(config);
        this.localAddress = config.getAcceptorAddress();
        this.useRouterBootstrap = config.shouldUseRouterBootstrap();
        this.publicBootstrapNodes = config.getPublicBootstrapNodes();
        this.bootstrapNodes = config.getBootstrapNodes();
        this.portMappers = portMappers;
        this.torrentRegistry = torrentRegistry;

        eventSource.onTorrentStarted(e -> onTorrentStarted(e.getTorrentId()));

        lifecycleBinder.onStartup(LifecycleBinding.bind(this::start).description("Initialize DHT facilities").async().build());
        lifecycleBinder.onShutdown("Shutdown DHT facilities", this::shutdown);
    }

    @NonNull
    private DHTConfiguration toMldhtConfig(@NonNull Config dhtConfig) {
        return new DHTConfiguration() {
            private final ConcurrentMap<InetAddress, Boolean> couldUseCacheMap = new ConcurrentHashMap<>();

            @NonNull
            @Override
            public PeerId getLocalPeerId() {
                return dhtConfig.getLocalPeerId();
            }

            @Override
            public int getListeningPort() {
                return dhtConfig.getListeningPort();
            }

            @Override
            public boolean noRouterBootstrap() {
                return true;
            }

            @Override
            public boolean allowMultiHoming() {
                return false;
            }

            @Override
            public Predicate<InetAddress> filterBindAddress() {
                return address -> {
                    Boolean couldUse = couldUseCacheMap.get(address);
                    if (couldUse != null) {
                        return couldUse;
                    }
                    boolean bothAnyLocal = address.isAnyLocalAddress() && localAddress.isAnyLocalAddress();
                    couldUse = bothAnyLocal || localAddress.equals(address);

                    couldUseCacheMap.put(address, couldUse);
                    return couldUse;
                };
            }
        };
    }

    private void start() {
        if (!dht.isRunning()) {
            try {
                dht.start(dhtConfig);
                if (useRouterBootstrap) {
                    publicBootstrapNodes.forEach(this::addNode);
                } else {
                    // assume that the environment is safe;
                    // might make this configuration more intelligent in future
                    dht.getNode().setTrustedNetMasks(Collections.singleton(NetMask.fromString("0.0.0.0/0")));
                }
                bootstrapNodes.forEach(this::addNode);
                mapPorts();

            } catch (Throwable e) {
                throw new BtException("Failed to start DHT", e);
            }
        }
    }

    private void mapPorts() {
        final int listeningPort = dhtConfig.getListeningPort();

        dht.getServerManager().getAllServers().forEach(s ->
                portMappers.forEach(m -> {
                    final InetAddress bindAddress = s.getBindAddress();
                    m.mapPort(listeningPort, bindAddress.toString(), UDP, "bt DHT");
                }));
    }

    private void onTorrentStarted(TorrentId torrentId) {
        torrentRegistry.getDescriptor(torrentId).ifPresent(td -> {
            DataDescriptor dd = td.getDataDescriptor();
            boolean seed = (dd != null) && (dd.getBitfield().getPiecesIncomplete() == 0);
            dht.getDatabase().store(new Key(torrentId.getBytes()),
                    PeerAddressDBItem.createFromAddress(config.getAcceptorAddress(), config.getAcceptorPort(), seed));
        });
    }

    private void shutdown() {
        dht.stop();
    }

    @Override
    public Stream<Peer> getPeers(Torrent torrent) {
        return getPeers(torrent.getTorrentId());
    }

    @Override
    public Stream<Peer> getPeers(TorrentId torrentId) {
        try {
            dht.getServerManager().awaitActiveServer().get();
            final PeerLookupTask lookup = dht.createPeerLookup(torrentId.getBytes());
            final StreamAdapter<Peer> streamAdapter = new StreamAdapter<>();
            lookup.setResultHandler((k, p) -> {
                Peer peer = InetPeer.build(p.getInetAddress(), p.getPort());
                streamAdapter.addItem(peer);
            });
            lookup.addListener(t -> {
                streamAdapter.finishStream();
                if (torrentRegistry.isSupportedAndActive(torrentId)) {
                    torrentRegistry.getDescriptor(torrentId).ifPresent(td -> {
                        DataDescriptor dd = td.getDataDescriptor();
                        boolean seed = (dd != null) && (dd.getBitfield().getPiecesIncomplete() == 0);
                        dht.announce(lookup, seed, config.getAcceptorPort());
                    });
                }
            });
            dht.getTaskManager().addTask(lookup);
            return streamAdapter.stream();
        } catch (Throwable e) {
            LogUtils.error(TAG, String.format("Unexpected error in peer lookup: %s. See DHT log file for diagnostic information.",
                    e.getMessage()), e);
            BtException btex = new BtException(String.format("Unexpected error in peer lookup: %s. Diagnostics:\n%s",
                    e.getMessage(), getDiagnostics()), e);
            LogUtils.error(TAG, "" + btex.getLocalizedMessage(), btex);
            throw btex;
        }
    }

    private void addNode(InetPeerAddress address) {
        addNode(address.getHostname(), address.getPort());
    }

    private void addNode(String hostname, int port) {
        dht.addDHTNode(hostname, port);
    }

    private String getDiagnostics() {
        StringWriter sw = new StringWriter();
        dht.printDiagnostics(new PrintWriter(sw));
        return sw.toString();
    }
}
