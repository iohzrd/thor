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

package threads.thor.bt.torrent;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import threads.thor.bt.data.DataDescriptorFactory;
import threads.thor.bt.data.Storage;
import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.service.RuntimeLifecycleBinder;

/**
 * Simple in-memory threads.torrent registry, that creates new descriptors upon request.
 *
 * <p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public final class TorrentRegistry {

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
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
