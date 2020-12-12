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

package threads.thor.bt.peer;

import androidx.annotation.NonNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import threads.LogUtils;
import threads.thor.bt.event.EventSink;
import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.InetPeer;
import threads.thor.bt.net.Peer;
import threads.thor.bt.runtime.Config;
import threads.thor.bt.service.RuntimeLifecycleBinder;
import threads.thor.bt.torrent.TorrentDescriptor;
import threads.thor.bt.torrent.TorrentRegistry;
import threads.thor.bt.tracker.AnnounceKey;
import threads.thor.bt.tracker.ITrackerService;

/**
 * <p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public final class PeerRegistry {

    private static final String TAG = PeerRegistry.class.getSimpleName();
    private final Peer localPeer;

    private final TorrentRegistry torrentRegistry;
    private final ITrackerService trackerService;
    private final EventSink eventSink;
    private final TrackerPeerSourceFactory trackerPeerSourceFactory;
    private final Set<PeerSourceFactory> extraPeerSourceFactories = new HashSet<>();

    private final ConcurrentMap<TorrentId, Set<AnnounceKey>> extraAnnounceKeys;
    private final ReentrantLock extraAnnounceKeysLock;


    public PeerRegistry(@NonNull RuntimeLifecycleBinder lifecycleBinder,
                        @NonNull TorrentRegistry torrentRegistry,
                        @NonNull ITrackerService trackerService,
                        @NonNull EventSink eventSink,
                        @NonNull Config config) {

        this.localPeer = InetPeer.builder(config.getAcceptorAddress(), config.getAcceptorPort())
                .peerId(config.getLocalPeerId())
                .build();

        this.torrentRegistry = torrentRegistry;
        this.trackerService = trackerService;
        this.eventSink = eventSink;
        this.trackerPeerSourceFactory = new TrackerPeerSourceFactory(trackerService, torrentRegistry,
                lifecycleBinder, config.getTrackerQueryInterval());

        this.extraAnnounceKeys = new ConcurrentHashMap<>();
        this.extraAnnounceKeysLock = new ReentrantLock();

        createExecutor(lifecycleBinder, config.getPeerDiscoveryInterval());
    }

    public void addPeerSourceFactory(@NonNull PeerSourceFactory factory) {
        extraPeerSourceFactories.add(factory);
    }

    private void createExecutor(RuntimeLifecycleBinder lifecycleBinder, Duration peerDiscoveryInterval) {
        ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "bt.peer.peer-collector"));
        lifecycleBinder.onStartup("Schedule periodic peer lookup", () -> executor.scheduleAtFixedRate(
                this::collectAndVisitPeers, 1, peerDiscoveryInterval.toMillis(), TimeUnit.MILLISECONDS));
        lifecycleBinder.onShutdown("Shutdown peer lookup scheduler", executor::shutdownNow);
    }

    private void collectAndVisitPeers() {
        torrentRegistry.getTorrentIds().forEach(torrentId -> {
            Optional<TorrentDescriptor> descriptor = torrentRegistry.getDescriptor(torrentId);
            if (descriptor.isPresent() && descriptor.get().isActive()) {
                Optional<Torrent> torrentOptional = torrentRegistry.getTorrent(torrentId);

                Optional<AnnounceKey> torrentAnnounceKey = torrentOptional.isPresent() ?
                        Optional.ofNullable(torrentOptional.get().getAnnounceKey()) : Optional.empty();

                Collection<AnnounceKey> extraTorrentAnnounceKeys = extraAnnounceKeys.get(torrentId);
                if (extraTorrentAnnounceKeys == null) {
                    queryTrackers(torrentId, torrentAnnounceKey, Collections.emptyList());
                } else if (torrentOptional.isPresent() && torrentOptional.get().isPrivate()) {
                    if (extraTorrentAnnounceKeys.size() > 0) {
                        // prevent violating private torrents' rule of "only one tracker"
                        LogUtils.info(TAG, "Will not query extra trackers for a private threads.torrent, id");
                    }
                } else {
                    // more announce keys might be added at the same time;
                    // querying all trackers can be time-consuming, so we make a copy of the collection
                    // to prevent blocking callers of addPeerSource(TorrentId, AnnounceKey) for too long
                    Collection<AnnounceKey> extraTorrentAnnounceKeysCopy;
                    extraAnnounceKeysLock.lock();
                    try {
                        extraTorrentAnnounceKeysCopy = new ArrayList<>(extraTorrentAnnounceKeys);
                    } finally {
                        extraAnnounceKeysLock.unlock();
                    }
                    queryTrackers(torrentId, torrentAnnounceKey, extraTorrentAnnounceKeysCopy);
                }

                // disallow querying peer sources other than the tracker for private torrents
                if ((!torrentOptional.isPresent() || !torrentOptional.get().isPrivate()) && !extraPeerSourceFactories.isEmpty()) {
                    extraPeerSourceFactories.forEach(factory ->
                            queryPeerSource(torrentId, factory.getPeerSource(torrentId)));
                }
            }
        });
    }

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
    }

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


    public void addPeerSource(TorrentId torrentId, AnnounceKey announceKey) {
        extraAnnounceKeysLock.lock();
        try {
            getOrCreateExtraAnnounceKeys(torrentId).add(announceKey);
        } finally {
            extraAnnounceKeysLock.unlock();
        }
    }

    private Set<AnnounceKey> getOrCreateExtraAnnounceKeys(TorrentId torrentId) {
        Set<AnnounceKey> announceKeys = extraAnnounceKeys.get(torrentId);
        if (announceKeys == null) {
            announceKeys = ConcurrentHashMap.newKeySet();
            Set<AnnounceKey> existing = extraAnnounceKeys.putIfAbsent(torrentId, announceKeys);
            if (existing != null) {
                announceKeys = existing;
            }
        }
        return announceKeys;
    }

    private boolean isLocal(Peer peer) {
        return peer.getInetAddress().equals(localPeer.getInetAddress())
                && localPeer.getPort() == peer.getPort();
    }

    public Peer getLocalPeer() {
        return localPeer;
    }
}
