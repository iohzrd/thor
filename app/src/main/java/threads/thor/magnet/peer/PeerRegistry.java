package threads.thor.magnet.peer;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import threads.LogUtils;
import threads.thor.Settings;
import threads.thor.magnet.event.EventSink;
import threads.thor.magnet.metainfo.Torrent;
import threads.thor.magnet.metainfo.TorrentId;
import threads.thor.magnet.net.InetPeer;
import threads.thor.magnet.net.Peer;
import threads.thor.magnet.net.PeerId;
import threads.thor.magnet.service.RuntimeLifecycleBinder;
import threads.thor.magnet.torrent.TorrentDescriptor;
import threads.thor.magnet.torrent.TorrentRegistry;

public final class PeerRegistry {

    private static final String TAG = PeerRegistry.class.getSimpleName();
    private final Peer localPeer;

    private final TorrentRegistry torrentRegistry;
    private final EventSink eventSink;
    private final Set<PeerSourceFactory> extraPeerSourceFactories = new HashSet<>();


    public PeerRegistry(@NonNull RuntimeLifecycleBinder lifecycleBinder,
                        @NonNull TorrentRegistry torrentRegistry,
                        @NonNull EventSink eventSink,
                        @NonNull PeerId peerId,
                        int acceptorPort) {

        this.localPeer = InetPeer.builder(Settings.acceptorAddress, acceptorPort)
                .peerId(peerId)
                .build();

        this.torrentRegistry = torrentRegistry;
        this.eventSink = eventSink;


        createExecutor(lifecycleBinder);
    }

    public void addPeerSourceFactory(@NonNull PeerSourceFactory factory) {
        extraPeerSourceFactories.add(factory);
    }

    private void createExecutor(RuntimeLifecycleBinder lifecycleBinder) {
        ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "bt.peer.peer-collector"));
        lifecycleBinder.onStartup("Schedule periodic peer lookup", () -> executor.scheduleAtFixedRate(
                this::collectAndVisitPeers, 1, Settings.peerDiscoveryInterval.toMillis(), TimeUnit.MILLISECONDS));
        lifecycleBinder.onShutdown("Shutdown peer lookup scheduler", executor::shutdownNow);
    }

    private void collectAndVisitPeers() {
        torrentRegistry.getTorrentIds().forEach(torrentId -> {
            Optional<TorrentDescriptor> descriptor = torrentRegistry.getDescriptor(torrentId);
            if (descriptor.isPresent() && descriptor.get().isActive()) {
                Optional<Torrent> torrentOptional = torrentRegistry.getTorrent(torrentId);


                // disallow querying peer sources other than the tracker for private torrents
                if ((!torrentOptional.isPresent() || !torrentOptional.get().isPrivate()) && !extraPeerSourceFactories.isEmpty()) {
                    extraPeerSourceFactories.forEach(factory ->
                            queryPeerSource(torrentId, factory.getPeerSource(torrentId)));
                }
            }
        });
    }

    /*
    private void queryTrackers(TorrentId torrentId, Optional<AnnounceKey> torrentAnnounceKey, Collection<AnnounceKey> extraAnnounceKeys) {
        torrentAnnounceKey.ifPresent(announceKey -> {
            try {
                queryTracker(torrentId, announceKey);
            } catch (Exception e) {
                LogUtils.error(TAG, "Error when querying tracker (threads.torrent's announce key): " + announceKey, e);
            }
        });
        extraAnnounceKeys.forEach(announceKey -> {
            try {
                queryTracker(torrentId, announceKey);
            } catch (Exception e) {
                LogUtils.error(TAG, "Error when querying tracker (extra announce key): " + announceKey, e);
            }
        });
    }


    private void queryTracker(TorrentId torrentId, AnnounceKey announceKey) {
        if (mightCreateTracker(announceKey)) {

            queryPeerSource(torrentId, trackerPeerSourceFactory.getPeerSource(torrentId, announceKey));
        }
    }

    private boolean mightCreateTracker(AnnounceKey announceKey) {
        if (announceKey.isMultiKey()) {
            // TODO: need some more sophisticated solution because some of the trackers might be supported
            for (List<String> tier : announceKey.getTrackerUrls()) {
                for (String trackerUrl : tier) {
                    if (!trackerService.isSupportedProtocol(trackerUrl)) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            return trackerService.isSupportedProtocol(announceKey.getTrackerUrl());
        }
    }*/

    private void queryPeerSource(TorrentId torrentId, PeerSource peerSource) {
        try {
            if (peerSource.update()) {
                Collection<Peer> discoveredPeers = peerSource.getPeers();
                Set<Peer> addedPeers = new HashSet<>();
                Iterator<Peer> iter = discoveredPeers.iterator();
                while (iter.hasNext()) {
                    Peer peer = iter.next();
                    if (!addedPeers.contains(peer)) {
                        addPeer(torrentId, peer);
                        addedPeers.add(peer);
                    }
                    iter.remove();
                }
            }
        } catch (Exception e) {
            LogUtils.error(TAG, "Error when querying peer source: " + peerSource, e);
        }
    }

    public void addPeer(TorrentId torrentId, Peer peer) {
        if (peer.isPortUnknown()) {
            throw new IllegalArgumentException("Peer's port is unknown: " + peer);
        } else if (peer.getPort() < 0 || peer.getPort() > 65535) {
            throw new IllegalArgumentException("Invalid port: " + peer.getPort());
        } else if (isLocal(peer)) {
            return;
        }
        eventSink.firePeerDiscovered(torrentId, peer);
    }


    private boolean isLocal(Peer peer) {
        return peer.getInetAddress().equals(localPeer.getInetAddress())
                && localPeer.getPort() == peer.getPort();
    }

    public Peer getLocalPeer() {
        return localPeer;
    }
}
