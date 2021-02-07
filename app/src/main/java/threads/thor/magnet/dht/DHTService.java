package threads.thor.magnet.dht;


import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import threads.LogUtils;
import threads.thor.Settings;
import threads.thor.magnet.data.DataDescriptor;
import threads.thor.magnet.event.EventSource;
import threads.thor.magnet.kad.DHT;
import threads.thor.magnet.kad.DHT.DHTtype;
import threads.thor.magnet.kad.Key;
import threads.thor.magnet.kad.PeerAddressDBItem;
import threads.thor.magnet.kad.tasks.PeerLookupTask;
import threads.thor.magnet.metainfo.TorrentId;
import threads.thor.magnet.net.InetPeer;
import threads.thor.magnet.net.InetPeerAddress;
import threads.thor.magnet.net.Peer;
import threads.thor.magnet.net.PeerId;
import threads.thor.magnet.net.portmapping.PortMapper;
import threads.thor.magnet.service.LifecycleBinding;
import threads.thor.magnet.service.NetworkUtil;
import threads.thor.magnet.service.RuntimeLifecycleBinder;
import threads.thor.magnet.torrent.TorrentRegistry;

import static threads.thor.magnet.net.portmapping.PortMapProtocol.UDP;

public class DHTService {
    private static final String TAG = DHTService.class.getSimpleName();


    private final DHT dht;
    private final int port;
    private final int acceptorPort;
    private final PeerId peerId;
    private final Set<PortMapper> portMappers;
    private final TorrentRegistry torrentRegistry;

    public DHTService(@NonNull RuntimeLifecycleBinder lifecycleBinder,
                      @NonNull PeerId peerId,
                      @NonNull Set<PortMapper> portMappers,
                      @NonNull TorrentRegistry torrentRegistry,
                      @NonNull EventSource eventSource,
                      int acceptorPort) {

        this.dht = new DHT(NetworkUtil.hasIpv6() ? DHTtype.IPV6_DHT : DHTtype.IPV4_DHT);
        this.acceptorPort = acceptorPort;
        this.portMappers = portMappers;
        this.torrentRegistry = torrentRegistry;

        eventSource.onTorrentStarted(e -> onTorrentStarted(e.getTorrentId()));
        this.peerId = peerId;
        this.port = nextFreePort();

        lifecycleBinder.onStartup(LifecycleBinding.bind(this::start).description("Initialize DHT facilities").async().build());
        lifecycleBinder.onShutdown("Shutdown DHT facilities", this::shutdown);
    }

    public static int nextFreePort() {
        int port = ThreadLocalRandom.current().nextInt(10001, 65535);
        while (true) {
            if (isLocalPortFree(port)) {
                return port;
            } else {
                port = ThreadLocalRandom.current().nextInt(10001, 65535);
            }
        }
    }

    private static boolean isLocalPortFree(int port) {
        try {
            new ServerSocket(port).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public int getPort() {
        return port;
    }

    private void start() {

        try {
            dht.start(peerId, port);
            Settings.BOOTSTRAP_NODES.forEach(this::addNode);
            mapPorts();
        } catch (Throwable e) {
            throw new RuntimeException("Failed to start DHT", e);
        }

    }

    private void mapPorts() {

        dht.getServerManager().getAllServers().forEach(s ->
                portMappers.forEach(m -> {
                    final InetAddress bindAddress = s.getBindAddress();
                    m.mapPort(port, bindAddress.toString(), UDP, "bt DHT");
                }));
    }

    private void onTorrentStarted(TorrentId torrentId) {
        InetAddress localAddress = NetworkUtil.getInetAddressFromNetworkInterfaces();
        torrentRegistry.getDescriptor(torrentId).ifPresent(td -> {
            DataDescriptor dd = td.getDataDescriptor();
            boolean seed = (dd != null) && (dd.getBitfield().getPiecesIncomplete() == 0);
            dht.getDatabase().store(new Key(torrentId.getBytes()),
                    PeerAddressDBItem.createFromAddress(localAddress, acceptorPort, seed));
        });
    }

    private void shutdown() {
        dht.stop();
    }


    public Stream<Peer> getPeers(TorrentId torrentId) {
        try {
            dht.getServerManager().awaitActiveServer().get();
            final PeerLookupTask lookup = dht.createPeerLookup(torrentId.getBytes());
            final StreamAdapter<Peer> streamAdapter = new StreamAdapter<>();

            Objects.requireNonNull(lookup);
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
                        dht.announce(lookup, seed, acceptorPort);
                    });
                }
            });
            dht.getTaskManager().addTask(lookup);
            return streamAdapter.stream();
        } catch (Throwable e) {
            LogUtils.error(TAG, String.format("Unexpected error in peer lookup: %s. See DHT log file for diagnostic information.",
                    e.getMessage()), e);
            throw new RuntimeException(e);
        }
    }

    private void addNode(InetPeerAddress address) {
        addNode(address.getHostname(), address.getPort());
    }

    private void addNode(String hostname, int port) {
        dht.addDHTNode(hostname, port);
    }

}