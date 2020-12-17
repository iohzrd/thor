package threads.thor.bt.peerexchange;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import threads.thor.bt.BtException;
import threads.thor.bt.IConsumers;
import threads.thor.bt.event.EventSource;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.ConnectionKey;
import threads.thor.bt.net.Peer;
import threads.thor.bt.peer.PeerSource;
import threads.thor.bt.peer.PeerSourceFactory;
import threads.thor.bt.protocol.Message;
import threads.thor.bt.protocol.extended.ExtendedHandshake;
import threads.thor.bt.service.RuntimeLifecycleBinder;
import threads.thor.bt.torrent.MessageConsumer;
import threads.thor.bt.torrent.MessageContext;

public class PeerExchangePeerSourceFactory implements PeerSourceFactory, IConsumers {

    private static final Duration CLEANER_INTERVAL = Duration.ofSeconds(37);

    private final Map<TorrentId, PeerExchangePeerSource> peerSources;

    private final Map<TorrentId, Queue<PeerEvent>> peerEvents;
    private final ReentrantReadWriteLock rwLock;

    private final Set<ConnectionKey> peers;
    private final Map<ConnectionKey, Long> lastSentPEXMessage;

    private final Duration minMessageInterval;
    private final Duration maxMessageInterval;
    private final int minEventsPerMessage;
    private final int maxEventsPerMessage;


    public PeerExchangePeerSourceFactory(EventSource eventSource,
                                         RuntimeLifecycleBinder lifecycleBinder,
                                         PeerExchangeConfig config) {
        this.peerSources = new ConcurrentHashMap<>();
        this.peerEvents = new ConcurrentHashMap<>();
        this.rwLock = new ReentrantReadWriteLock();
        this.peers = ConcurrentHashMap.newKeySet();
        this.lastSentPEXMessage = new ConcurrentHashMap<>();
        if (config.getMaxMessageInterval().compareTo(config.getMinMessageInterval()) < 0) {
            throw new IllegalArgumentException("Max message interval is greater than min interval");
        }
        this.minMessageInterval = config.getMinMessageInterval();
        this.maxMessageInterval = config.getMaxMessageInterval();
        this.minEventsPerMessage = config.getMinEventsPerMessage();
        this.maxEventsPerMessage = config.getMaxEventsPerMessage();

        eventSource.onPeerConnected(e -> onPeerConnected(e.getConnectionKey()))
                .onPeerDisconnected(e -> onPeerDisconnected(e.getConnectionKey()));

        ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "bt.peerexchange.cleaner"));
        lifecycleBinder.onStartup("Schedule periodic cleanup of PEX messages", () -> executor.scheduleAtFixedRate(
                new Cleaner(), CLEANER_INTERVAL.toMillis(), CLEANER_INTERVAL.toMillis(), TimeUnit.MILLISECONDS));
        lifecycleBinder.onShutdown("Shutdown PEX cleanup scheduler", executor::shutdownNow);
    }

    private void onPeerConnected(ConnectionKey connectionKey) {
        getPeerEvents(connectionKey.getTorrentId())
                .add(PeerEvent.added(connectionKey.getPeer()));
    }

    private void onPeerDisconnected(ConnectionKey connectionKey) {
        getPeerEvents(connectionKey.getTorrentId())
                .add(PeerEvent.dropped(connectionKey.getPeer()));
        peers.remove(connectionKey);
        lastSentPEXMessage.remove(connectionKey);
    }

    private Queue<PeerEvent> getPeerEvents(TorrentId torrentId) {
        Queue<PeerEvent> events = peerEvents.get(torrentId);
        if (events == null) {
            events = new PriorityBlockingQueue<>();
            Queue<PeerEvent> existing = peerEvents.putIfAbsent(torrentId, events);
            if (existing != null) {
                events = existing;
            }
        }
        return events;
    }

    @Override
    public PeerSource getPeerSource(TorrentId torrentId) {
        return getOrCreatePeerSource(torrentId);
    }

    private PeerExchangePeerSource getOrCreatePeerSource(TorrentId torrentId) {
        PeerExchangePeerSource peerSource = peerSources.get(torrentId);
        if (peerSource == null) {
            peerSource = new PeerExchangePeerSource();
            PeerExchangePeerSource existing = peerSources.putIfAbsent(torrentId, peerSource);
            if (existing != null) {
                peerSource = existing;
            }
        }
        return peerSource;
    }

    @Override
    public void doConsume(Message message, MessageContext messageContext) {
        if (message instanceof ExtendedHandshake) {
            consume((ExtendedHandshake) message, messageContext);
        }
        if (message instanceof PeerExchange) {
            consume((PeerExchange) message, messageContext);
        }
    }

    @Override
    public List<MessageConsumer<? extends Message>> getConsumers() {
        List<MessageConsumer<? extends Message>> list = new ArrayList<>();
        list.add(new MessageConsumer<PeerExchange>() {
            @Override
            public Class<PeerExchange> getConsumedType() {
                return PeerExchange.class;
            }

            @Override
            public void consume(PeerExchange message, MessageContext context) {
                doConsume(message, context);
            }
        });
        list.add(new MessageConsumer<ExtendedHandshake>() {
            @Override
            public Class<ExtendedHandshake> getConsumedType() {
                return ExtendedHandshake.class;
            }

            @Override
            public void consume(ExtendedHandshake message, MessageContext context) {
                doConsume(message, context);
            }
        });
        return list;
    }


    private void consume(ExtendedHandshake handshake, MessageContext messageContext) {
        if (handshake.getSupportedMessageTypes().contains("ut_pex")) {
            // TODO: peer may eventually turn off the PEX extension
            // moreover the extended handshake message type map is additive,
            // so we can't learn about the peer turning off extensions solely from the message
            peers.add(messageContext.getConnectionKey());
        }
    }


    private void consume(PeerExchange message, MessageContext messageContext) {
        getOrCreatePeerSource(messageContext.getTorrentId()).addMessage(message);
    }


    public void produce(Consumer<Message> messageConsumer, MessageContext messageContext) {
        ConnectionKey connectionKey = messageContext.getConnectionKey();
        long currentTime = System.currentTimeMillis();
        long lastSentPEXMessageToPeer = lastSentPEXMessage.getOrDefault(connectionKey, 0L);

        if (peers.contains(connectionKey) && (currentTime - lastSentPEXMessageToPeer) >= minMessageInterval.toMillis()) {
            List<PeerEvent> events = new ArrayList<>();

            rwLock.readLock().lock();
            try {
                Queue<PeerEvent> torrentPeerEvents = getPeerEvents(messageContext.getTorrentId());
                for (PeerEvent event : torrentPeerEvents) {
                    if (event.getInstant() - lastSentPEXMessageToPeer >= 0) {
                        Peer exchangedPeer = event.getPeer();
                        // don't send PEX message if anything of the above is true:
                        // - we don't know the listening port of the event's peer yet
                        // - we don't know the listening port of the current connection's peer yet
                        // - event's peer and connection's peer are the same
                        if (!exchangedPeer.isPortUnknown()
                                && !connectionKey.getPeer().isPortUnknown()
                                && !exchangedPeer.getInetAddress().equals(connectionKey.getPeer().getInetAddress())
                                && exchangedPeer.getPort() != connectionKey.getRemotePort()) {
                            events.add(event);
                        }
                    } else {
                        break;
                    }
                    if (events.size() >= maxEventsPerMessage) {
                        break;
                    }
                }
            } finally {
                rwLock.readLock().unlock();
            }

            if (events.size() >= minEventsPerMessage ||
                    (!events.isEmpty() && (currentTime - lastSentPEXMessageToPeer >= maxMessageInterval.toMillis()))) {
                lastSentPEXMessage.put(connectionKey, currentTime);
                PeerExchange.Builder messageBuilder = PeerExchange.builder();
                events.forEach(event -> {
                    switch (event.getType()) {
                        case ADDED: {
                            messageBuilder.added(event.getPeer());
                            break;
                        }
                        case DROPPED: {
                            messageBuilder.dropped(event.getPeer());
                            break;
                        }
                        default: {
                            throw new BtException("Unknown event type: " + event.getType().name());
                        }
                    }
                });
                messageConsumer.accept(messageBuilder.build());
            }
        }
    }

    private class Cleaner implements Runnable {

        @Override
        public void run() {
            rwLock.writeLock().lock();
            try {
                long lruEventTime = lastSentPEXMessage.values().stream()
                        .reduce(Long.MAX_VALUE, (a, b) -> (a < b) ? a : b);


                PeerEvent event;
                for (Queue<PeerEvent> events : peerEvents.values()) {
                    while ((event = events.peek()) != null && event.getInstant() <= lruEventTime) {
                        events.poll();
                    }
                }


            } finally {
                rwLock.writeLock().unlock();
            }
        }
    }
}
