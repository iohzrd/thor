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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.peer.PeerSource;
import threads.thor.bt.peer.PeerSourceFactory;
import threads.thor.bt.service.RuntimeLifecycleBinder;

/**
 * Factory of DHT peer sources
 *
 * @since 1.1
 */
public class DHTPeerSourceFactory implements PeerSourceFactory {

    private final DHTService dhtService;
    private final ExecutorService executor;

    private final Map<TorrentId, DHTPeerSource> peerSources;

    public DHTPeerSourceFactory(RuntimeLifecycleBinder lifecycleBinder,
                                DHTService dhtService) {
        this.dhtService = dhtService;
        this.executor = Executors.newCachedThreadPool(r -> new Thread(r, "threads.thor.bt.dht.executor"));
        lifecycleBinder.onShutdown("Shutdown DHT peer sources", executor::shutdownNow);

        this.peerSources = new ConcurrentHashMap<>();
    }

    @Override
    public PeerSource getPeerSource(TorrentId torrentId) {
        DHTPeerSource peerSource = peerSources.get(torrentId);
        if (peerSource == null) {
            peerSource = new DHTPeerSource(torrentId, dhtService, executor);
            DHTPeerSource existing = peerSources.putIfAbsent(torrentId, peerSource);
            if (existing != null) {
                peerSource = existing;
            }
        }
        return peerSource;
    }
}
