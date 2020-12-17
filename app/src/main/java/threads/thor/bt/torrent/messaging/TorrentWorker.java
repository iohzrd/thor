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

package threads.thor.bt.torrent.messaging;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

import threads.thor.bt.Config;
import threads.thor.bt.data.Bitfield;
import threads.thor.bt.event.EventSource;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.ConnectionKey;
import threads.thor.bt.net.IConnectionSource;
import threads.thor.bt.net.IMessageDispatcher;
import threads.thor.bt.net.Peer;
import threads.thor.bt.protocol.Have;
import threads.thor.bt.protocol.Interested;
import threads.thor.bt.protocol.Message;
import threads.thor.bt.protocol.NotInterested;
import threads.thor.bt.torrent.PieceStatistics;

/**
 * Manages peer workers.
 *
 * @since 1.0
 */
public class TorrentWorker {

    private static final Duration UPDATE_ASSIGNMENTS_OPTIONAL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration UPDATE_ASSIGNMENTS_MANDATORY_INTERVAL = Duration.ofSeconds(5);
    private final IConnectionSource connectionSource;
    private final int MAX_CONCURRENT_ACTIVE_CONNECTIONS;
    private final int MAX_TOTAL_CONNECTIONS;
    private final TorrentId torrentId;
    private final IMessageDispatcher dispatcher;
    private final Config config;
    private final IPeerWorkerFactory peerWorkerFactory;
    private final ConcurrentMap<ConnectionKey, PieceAnnouncingPeerWorker> peerMap;
    private final Map<ConnectionKey, Long> timeoutedPeers;
    private final Queue<ConnectionKey> disconnectedPeers;
    private final Map<ConnectionKey, Message> interestUpdates;
    private final Supplier<Bitfield> bitfieldSupplier;
    private final Supplier<Assignments> assignmentsSupplier;
    private final Supplier<PieceStatistics> statisticsSupplier;
    private long lastUpdatedAssignments;

    public TorrentWorker(TorrentId torrentId,
                         IMessageDispatcher dispatcher,
                         IConnectionSource connectionSource,
                         IPeerWorkerFactory peerWorkerFactory,
                         Supplier<Bitfield> bitfieldSupplier,
                         Supplier<Assignments> assignmentsSupplier,
                         Supplier<PieceStatistics> statisticsSupplier,
                         EventSource eventSource,
                         Config config) {
        this.torrentId = torrentId;
        this.dispatcher = dispatcher;
        this.config = config;

        this.connectionSource = connectionSource;
        this.peerWorkerFactory = peerWorkerFactory;
        this.peerMap = new ConcurrentHashMap<>();
        this.MAX_CONCURRENT_ACTIVE_CONNECTIONS = config.getMaxConcurrentlyActivePeerConnectionsPerTorrent();
        this.MAX_TOTAL_CONNECTIONS = config.getMaxPeerConnectionsPerTorrent();
        this.timeoutedPeers = new ConcurrentHashMap<>();
        this.disconnectedPeers = new LinkedBlockingQueue<>();
        this.interestUpdates = new ConcurrentHashMap<>();

        this.bitfieldSupplier = bitfieldSupplier;
        this.assignmentsSupplier = assignmentsSupplier;
        this.statisticsSupplier = statisticsSupplier;

        eventSource.onPeerDiscovered(e -> {
            if (torrentId.equals(e.getTorrentId())) {
                onPeerDiscovered(e.getPeer());
            }
        });

        eventSource.onPeerConnected(e -> {
            if (torrentId.equals(e.getTorrentId())) {
                onPeerConnected(e.getConnectionKey());
            }
        });

        eventSource.onPeerDisconnected(e -> {
            if (torrentId.equals(e.getTorrentId())) {
                onPeerDisconnected(e.getConnectionKey());
            }
        });
    }

    private Bitfield getBitfield() {
        return bitfieldSupplier.get();
    }

    private Assignments getAssignments() {
        return assignmentsSupplier.get();
    }

    private PieceStatistics getStatistics() {
        return statisticsSupplier.get();
    }

    /**
     * Called when a peer joins the threads.torrent processing session.
     *
     * @since 1.0
     */
    private void addPeer(ConnectionKey connectionKey) {
        PieceAnnouncingPeerWorker worker = createPeerWorker(connectionKey);
        PieceAnnouncingPeerWorker existing = peerMap.putIfAbsent(connectionKey, worker);
        if (existing == null) {
            dispatcher.addMessageConsumer(connectionKey, message -> consume(connectionKey, message));
            dispatcher.addMessageSupplier(connectionKey, () -> produce(connectionKey));
        }
    }

    private void consume(ConnectionKey connectionKey, Message message) {
        getWorker(connectionKey).ifPresent(worker -> worker.accept(message));
    }

    private Message produce(ConnectionKey connectionKey) {
        Message message = null;

        Optional<PieceAnnouncingPeerWorker> workerOptional = getWorker(connectionKey);
        if (workerOptional.isPresent()) {
            PieceAnnouncingPeerWorker worker = workerOptional.get();
            Bitfield bitfield = getBitfield();
            Assignments assignments = getAssignments();

            if (bitfield != null && assignments != null && (bitfield.getPiecesRemaining() > 0 || assignments.count() > 0)) {
                inspectAssignment(connectionKey, worker, assignments);
                if (shouldUpdateAssignments(assignments)) {
                    processDisconnectedPeers(assignments, getStatistics());
                    processTimeoutedPeers();
                    updateAssignments(assignments);
                }
                Message interestUpdate = interestUpdates.remove(connectionKey);
                message = (interestUpdate == null) ? worker.get() : interestUpdate;
            } else {
                message = worker.get();
            }
        }

        return message;
    }

    private Optional<PieceAnnouncingPeerWorker> getWorker(ConnectionKey connectionKey) {
        return Optional.ofNullable(peerMap.get(connectionKey));
    }

    private void inspectAssignment(ConnectionKey connectionKey, PeerWorker peerWorker, Assignments assignments) {
        ConnectionState connectionState = peerWorker.getConnectionState();
        Assignment assignment = assignments.get(connectionKey);
        if (assignment != null) {
            if (assignment.getStatus() == Assignment.Status.TIMEOUT) {
                timeoutedPeers.put(connectionKey, System.currentTimeMillis());
                assignments.remove(assignment);

            } else if (connectionState.isPeerChoking()) {
                assignments.remove(assignment);

            }
        } else if (!connectionState.isPeerChoking()) {
            if (mightCreateMoreAssignments(assignments)) {
                assignments.assign(connectionKey)
                        .ifPresent(newAssignment -> newAssignment.start(connectionState));
            }
        }
    }

    private boolean shouldUpdateAssignments(Assignments assignments) {
        return (timeSinceLastUpdated() > UPDATE_ASSIGNMENTS_OPTIONAL_INTERVAL.toMillis()
                && mightUseMoreAssignees(assignments))
                || timeSinceLastUpdated() > UPDATE_ASSIGNMENTS_MANDATORY_INTERVAL.toMillis();
    }

    private boolean mightUseMoreAssignees(Assignments assignments) {
        return assignments.count() < MAX_CONCURRENT_ACTIVE_CONNECTIONS;
    }

    private boolean mightCreateMoreAssignments(Assignments assignments) {
        return assignments.count() < MAX_CONCURRENT_ACTIVE_CONNECTIONS;
    }

    private long timeSinceLastUpdated() {
        return System.currentTimeMillis() - lastUpdatedAssignments;
    }

    private void processDisconnectedPeers(Assignments assignments, PieceStatistics statistics) {
        ConnectionKey disconnectedPeer;
        while ((disconnectedPeer = disconnectedPeers.poll()) != null) {
            if (assignments != null) {
                Assignment assignment = assignments.get(disconnectedPeer);
                if (assignment != null) {
                    assignments.remove(assignment);

                }
            }
            timeoutedPeers.remove(disconnectedPeer);
            if (statistics != null) {
                statistics.removeBitfield(disconnectedPeer);
            }
        }
    }

    private void processTimeoutedPeers() {
        timeoutedPeers.entrySet().removeIf(entry ->
                System.currentTimeMillis() - entry.getValue() >=
                        config.getTimeoutedAssignmentPeerBanDuration().toMillis());
    }

    private void updateAssignments(Assignments assignments) {
        interestUpdates.clear();

        Set<ConnectionKey> ready = new HashSet<>();
        Set<ConnectionKey> choking = new HashSet<>();

        peerMap.forEach((peer, worker) -> {
            boolean timeouted = timeoutedPeers.containsKey(peer);
            boolean disconnected = disconnectedPeers.contains(peer);
            if (!timeouted && !disconnected) {
                if (worker.getConnectionState().isPeerChoking()) {
                    choking.add(peer);
                } else {
                    ready.add(peer);
                }
            }
        });

        Set<ConnectionKey> interesting = assignments.update(ready, choking);

        ready.stream().filter(peer -> !interesting.contains(peer)).forEach(peer -> getWorker(peer).ifPresent(worker -> {
            ConnectionState connectionState = worker.getConnectionState();
            if (connectionState.isInterested()) {
                interestUpdates.put(peer, NotInterested.instance());
                connectionState.setInterested(false);
            }
        }));

        choking.forEach(peer -> getWorker(peer).ifPresent(worker -> {
            ConnectionState connectionState = worker.getConnectionState();
            if (interesting.contains(peer)) {
                if (!connectionState.isInterested()) {
                    interestUpdates.put(peer, Interested.instance());
                    connectionState.setInterested(true);
                }
            } else if (connectionState.isInterested()) {
                interestUpdates.put(peer, NotInterested.instance());
                connectionState.setInterested(false);
            }
        }));

        lastUpdatedAssignments = System.currentTimeMillis();
    }

    private PieceAnnouncingPeerWorker createPeerWorker(ConnectionKey connectionKey) {
        return new PieceAnnouncingPeerWorker(peerWorkerFactory.createPeerWorker(connectionKey));
    }

    /**
     * Called when a peer leaves the threads.torrent processing session.
     *
     * @since 1.0
     */
    private void removePeer(ConnectionKey connectionKey) {
        PeerWorker removed = peerMap.remove(connectionKey);
        if (removed != null) {
            disconnectedPeers.add(connectionKey);
        }
    }

    /**
     * Get all peers, that this threads.torrent worker is currently working with.
     *
     * @since 1.9
     */
    public Set<ConnectionKey> getPeers() {
        return peerMap.keySet();
    }

    /**
     * Get the current state of a connection with a particular peer.
     *
     * @return Connection state or null, if the peer is not connected to this threads.torrent worker
     * @since 1.0
     */
    public ConnectionState getConnectionState(ConnectionKey connectionKey) {
        PeerWorker worker = peerMap.get(connectionKey);
        return (worker == null) ? null : worker.getConnectionState();
    }

    private synchronized void onPeerDiscovered(Peer peer) {
        // TODO: Store discovered peers to use them later,
        // when some of the currently connected peers disconnects
        if (mightAddPeer()) {
            connectionSource.getConnectionAsync(peer, torrentId);
        }
    }

    private synchronized void onPeerConnected(ConnectionKey connectionKey) {
        if (mightAddPeer()) {
            addPeer(connectionKey);
        }
    }

    private boolean mightAddPeer() {
        return getPeers().size() < MAX_TOTAL_CONNECTIONS;
    }

    private synchronized void onPeerDisconnected(ConnectionKey connectionKey) {
        removePeer(connectionKey);
    }

    private class PieceAnnouncingPeerWorker implements PeerWorker {

        private final PeerWorker delegate;
        private final Queue<Have> pieceAnnouncements;

        PieceAnnouncingPeerWorker(PeerWorker delegate) {
            this.delegate = delegate;
            this.pieceAnnouncements = new ConcurrentLinkedQueue<>();
        }

        @Override
        public ConnectionState getConnectionState() {
            return delegate.getConnectionState();
        }

        @Override
        public void accept(Message message) {
            delegate.accept(message);
        }

        @Override
        public Message get() {
            Message message = pieceAnnouncements.poll();
            if (message != null) {
                return message;
            }

            message = delegate.get();
            if (message != null && Have.class.equals(message.getClass())) {
                Have have = (Have) message;
                peerMap.values().forEach(worker -> {
                    if (this != worker) {
                        worker.getPieceAnnouncements().add(have);
                    }
                });
            }
            return message;
        }

        Queue<Have> getPieceAnnouncements() {
            return pieceAnnouncements;
        }
    }
}
