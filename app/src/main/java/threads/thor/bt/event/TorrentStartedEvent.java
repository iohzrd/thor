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

import threads.thor.bt.metainfo.TorrentId;

/**
 * Indicates, that processing of some threads.torrent has begun.
 *
 * @since 1.5
 */
public class TorrentStartedEvent extends BaseEvent implements TorrentEvent {

    private final TorrentId torrentId;

    TorrentStartedEvent(long id, long timestamp, TorrentId torrentId) {
        super(id, timestamp);
        this.torrentId = torrentId;
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId;
    }

    @NonNull
    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] id {" + getId() + "}, timestamp {" + getTimestamp() +
                "}, threads.torrent {" + torrentId + "}";
    }
}
