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

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Stream;

import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.Peer;
import threads.thor.bt.peer.ScheduledPeerSource;

/**
 * @since 1.1
 */
public class DHTPeerSource extends ScheduledPeerSource {


    private static final int MAX_PEERS_PER_COLLECTION = 50;

    private final TorrentId torrentId;
    private final DHTService dhtService;

    DHTPeerSource(TorrentId torrentId, DHTService dhtService, ExecutorService executor) {
        super(executor);
        this.torrentId = torrentId;
        this.dhtService = dhtService;
    }

    @Override
    protected void collectPeers(Consumer<Peer> peerConsumer) {
        Stream<Peer> peerStream = dhtService.getPeers(torrentId).limit(MAX_PEERS_PER_COLLECTION);
        peerStream.forEach(peerConsumer);
    }
}
