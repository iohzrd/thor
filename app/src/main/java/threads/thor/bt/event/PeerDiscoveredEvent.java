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

package threads.thor.bt.event;

import androidx.annotation.NonNull;

import java.util.Objects;

import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.Peer;

/**
 * Indicates, that a new peer has been discovered for some threads.torrent.
 *
 * @since 1.5
 */
public class PeerDiscoveredEvent extends BaseEvent implements TorrentEvent {

    private final TorrentId torrentId;
    private final Peer peer;

    PeerDiscoveredEvent(long id, long timestamp, TorrentId torrentId, Peer peer) {
        super(id, timestamp);
        this.torrentId = Objects.requireNonNull(torrentId);
        this.peer = Objects.requireNonNull(peer);
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId;
    }

    /**
     * @since 1.5
     */
    public Peer getPeer() {
        return peer;
    }

    @NonNull
    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] id {" + getId() + "}, timestamp {" + getTimestamp() +
                "}, threads.torrent {" + torrentId + "}, peer {" + peer + "}";
    }
}
