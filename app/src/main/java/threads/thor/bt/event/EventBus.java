package threads.thor.bt.event;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import threads.LogUtils;
import threads.thor.bt.data.Bitfield;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.ConnectionKey;
import threads.thor.bt.net.Peer;

public class EventBus implements EventSink, EventSource {

    private static final String TAG = EventBus.class.getSimpleName();
    private final ConcurrentMap<Class<? extends BaseEvent>, Collection<Consumer<? extends BaseEvent>>> listeners;

    private final ReentrantReadWriteLock eventLock;

    private long idSequence;

    public EventBus() {
        this.listeners = new ConcurrentHashMap<>();
        this.eventLock = new ReentrantReadWriteLock();
    }

    @Override
    public synchronized void firePeerDiscovered(TorrentId torrentId, Peer peer) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(PeerDiscoveredEvent.class)) {
            long id = nextId();
            fireEvent(new PeerDiscoveredEvent(id, timestamp, torrentId, peer));
        }
    }

    @Override
    public synchronized void firePeerConnected(ConnectionKey connectionKey) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(PeerConnectedEvent.class)) {
            long id = nextId();
            fireEvent(new PeerConnectedEvent(id, timestamp, connectionKey));
        }
    }

    @Override
    public synchronized void firePeerDisconnected(ConnectionKey connectionKey) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(PeerDisconnectedEvent.class)) {
            long id = nextId();
            fireEvent(new PeerDisconnectedEvent(id, timestamp, connectionKey));
        }
    }

    @Override
    public void firePeerBitfieldUpdated(TorrentId torrentId, ConnectionKey connectionKey, Bitfield bitfield) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(PeerBitfieldUpdatedEvent.class)) {
            long id = nextId();
            fireEvent(new PeerBitfieldUpdatedEvent(id, timestamp, connectionKey, bitfield));
        }
    }

    @Override
    public void fireTorrentStarted(TorrentId torrentId) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(TorrentStartedEvent.class)) {
            long id = nextId();
            fireEvent(new TorrentStartedEvent(id, timestamp, torrentId));
        }
    }

    @Override
    public void fireMetadataAvailable(TorrentId torrentId) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(MetadataAvailableEvent.class)) {
            long id = nextId();
            fireEvent(new MetadataAvailableEvent(id, timestamp, torrentId));
        }
    }

    @Override
    public void fireTorrentStopped(TorrentId torrentId) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(TorrentStoppedEvent.class)) {
            long id = nextId();
            fireEvent(new TorrentStoppedEvent(id, timestamp, torrentId));
        }
    }

    @Override
    public void firePieceVerified(TorrentId torrentId, int pieceIndex) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(PieceVerifiedEvent.class)) {
            long id = nextId();
            fireEvent(new PieceVerifiedEvent(id, timestamp, torrentId, pieceIndex));
        }
    }

    private boolean hasListeners(Class<? extends BaseEvent> eventType) {
        Collection<Consumer<? extends BaseEvent>> listeners = this.listeners.get(eventType);
        return listeners != null && !listeners.isEmpty();
    }

    private synchronized long nextId() {
        return ++idSequence;
    }

    private <E extends BaseEvent> void fireEvent(E event) {
        eventLock.readLock().lock();
        try {
            Collection<Consumer<? extends BaseEvent>> listeners = this.listeners.get(event.getClass());

            if (listeners != null && !listeners.isEmpty()) {
                for (Consumer<? extends BaseEvent> listener : listeners) {
                    @SuppressWarnings("unchecked")
                    Consumer<E> _listener = (Consumer<E>) listener;
                    _listener.accept(event);
                }
            }
        } finally {
            eventLock.readLock().unlock();
        }
    }

    @Override
    public void onPeerDiscovered(Consumer<PeerDiscoveredEvent> listener) {
        addListener(PeerDiscoveredEvent.class, listener);
    }

    @Override
    public EventSource onPeerConnected(Consumer<PeerConnectedEvent> listener) {
        addListener(PeerConnectedEvent.class, listener);
        return this;
    }

    @Override
    public void onPeerDisconnected(Consumer<PeerDisconnectedEvent> listener) {
        addListener(PeerDisconnectedEvent.class, listener);
    }

    @Override
    public EventSource onPeerBitfieldUpdated(Consumer<PeerBitfieldUpdatedEvent> listener) {
        addListener(PeerBitfieldUpdatedEvent.class, listener);
        return this;
    }

    @Override
    public void onTorrentStarted(Consumer<TorrentStartedEvent> listener) {
        addListener(TorrentStartedEvent.class, listener);
    }

    @Override
    public EventSource onMetadataAvailable(Consumer<MetadataAvailableEvent> listener) {
        addListener(MetadataAvailableEvent.class, listener);
        return this;
    }

    @Override
    public void onTorrentStopped(Consumer<TorrentStoppedEvent> listener) {
        addListener(TorrentStoppedEvent.class, listener);
    }

    @Override
    public EventSource onPieceVerified(Consumer<PieceVerifiedEvent> listener) {
        addListener(PieceVerifiedEvent.class, listener);
        return this;
    }


    public boolean removePieceVerified(Consumer<PieceVerifiedEvent> listener) {
        return removeListener(listener);
    }

    private <E extends BaseEvent> boolean removeListener(@NonNull Consumer<E> listener) {
        Collection<Consumer<? extends BaseEvent>> listeners = this.listeners.get(PieceVerifiedEvent.class);
        if (listeners != null) {
            return listeners.remove(listener);
        }
        return false;
    }

    private <E extends BaseEvent> void addListener(Class<E> eventType, Consumer<E> listener) {
        Collection<Consumer<? extends BaseEvent>> listeners = this.listeners.get(eventType);
        if (listeners == null) {
            listeners = ConcurrentHashMap.newKeySet();
            Collection<Consumer<? extends BaseEvent>> existing = this.listeners.putIfAbsent(eventType, listeners);
            if (existing != null) {
                listeners = existing;
            }
        }

        eventLock.writeLock().lock();
        try {
            Consumer<E> safeListener = event -> {
                try {
                    listener.accept(event);
                } catch (Exception ex) {
                    LogUtils.error(TAG, ex);
                }
            };
            listeners.add(safeListener);
        } finally {
            eventLock.writeLock().unlock();
        }
    }
}
