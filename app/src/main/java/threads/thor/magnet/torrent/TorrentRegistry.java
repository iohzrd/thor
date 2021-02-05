package threads.thor.magnet.torrent;


import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import threads.LogUtils;
import threads.thor.magnet.data.DataDescriptorFactory;
import threads.thor.magnet.data.Storage;
import threads.thor.magnet.metainfo.Torrent;
import threads.thor.magnet.metainfo.TorrentId;
import threads.thor.magnet.service.RuntimeLifecycleBinder;

public final class TorrentRegistry {
    private static final String TAG = TorrentRegistry.class.getSimpleName();
    private final DataDescriptorFactory dataDescriptorFactory;
    private final RuntimeLifecycleBinder lifecycleBinder;

    private final Set<TorrentId> torrentIds;
    private final ConcurrentMap<TorrentId, Torrent> torrents;
    private final ConcurrentMap<TorrentId, TorrentDescriptor> descriptors;

    public TorrentRegistry(DataDescriptorFactory dataDescriptorFactory,
                           RuntimeLifecycleBinder lifecycleBinder) {

        this.dataDescriptorFactory = dataDescriptorFactory;
        this.lifecycleBinder = lifecycleBinder;

        this.torrentIds = ConcurrentHashMap.newKeySet();
        this.torrents = new ConcurrentHashMap<>();
        this.descriptors = new ConcurrentHashMap<>();
    }

    public Collection<TorrentId> getTorrentIds() {
        return Collections.unmodifiableCollection(torrentIds);
    }

    public Optional<Torrent> getTorrent(TorrentId torrentId) {
        Objects.requireNonNull(torrentId, "Missing threads.torrent ID");
        return Optional.ofNullable(torrents.get(torrentId));
    }


    public Optional<TorrentDescriptor> getDescriptor(TorrentId torrentId) {
        Objects.requireNonNull(torrentId, "Missing threads.torrent ID");
        return Optional.ofNullable(descriptors.get(torrentId));
    }

    public TorrentDescriptor register(Torrent torrent, Storage storage) {
        TorrentId torrentId = torrent.getTorrentId();

        TorrentDescriptor descriptor = descriptors.get(torrentId);
        if (descriptor != null) {
            if (descriptor.getDataDescriptor() != null) {
                throw new IllegalStateException(
                        "Torrent already registered and data descriptor created: " + torrent.getTorrentId());
            }
            descriptor.setDataDescriptor(dataDescriptorFactory.createDescriptor(torrent, storage));

        } else {
            descriptor = new TorrentDescriptor();
            descriptor.setDataDescriptor(dataDescriptorFactory.createDescriptor(torrent, storage));

            TorrentDescriptor existing = descriptors.putIfAbsent(torrentId, descriptor);
            if (existing != null) {
                descriptor = existing;
            } else {
                torrentIds.add(torrentId);
                addShutdownHook(torrentId, descriptor);
            }
        }

        torrents.putIfAbsent(torrentId, torrent);
        return descriptor;
    }

    public TorrentDescriptor register(TorrentId torrentId) {
        return getDescriptor(torrentId).orElseGet(() -> {
            TorrentDescriptor descriptor = new TorrentDescriptor();

            TorrentDescriptor existing = descriptors.putIfAbsent(torrentId, descriptor);
            if (existing != null) {
                descriptor = existing;
            } else {
                torrentIds.add(torrentId);
                addShutdownHook(torrentId, descriptor);
            }

            return descriptor;
        });
    }

    public boolean isSupportedAndActive(TorrentId torrentId) {
        Optional<TorrentDescriptor> descriptor = getDescriptor(torrentId);
        // it's OK if descriptor is not present -- threads.torrent might be being fetched at the time
        return getTorrentIds().contains(torrentId)
                && (!descriptor.isPresent() || descriptor.get().isActive());
    }

    private void addShutdownHook(TorrentId torrentId, TorrentDescriptor descriptor) {
        lifecycleBinder.onShutdown("Closing data descriptor for threads.torrent ID: " + torrentId, () -> {
            if (descriptor.getDataDescriptor() != null) {
                try {
                    descriptor.getDataDescriptor().close();
                } catch (Throwable e) {
                    LogUtils.error(TAG, e);
                }
            }
        });
    }
}
