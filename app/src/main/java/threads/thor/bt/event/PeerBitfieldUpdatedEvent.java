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

import threads.thor.bt.data.Bitfield;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.ConnectionKey;
import threads.thor.bt.net.Peer;

/**
 * Indicates, that local information about some peer's data has been updated.
 *
 * @since 1.5
 */
public class PeerBitfieldUpdatedEvent extends BaseEvent implements TorrentEvent {

    private final ConnectionKey connectionKey;
    private final Bitfield bitfield;

    PeerBitfieldUpdatedEvent(long id, long timestamp,
                             ConnectionKey connectionKey, Bitfield bitfield) {
        super(id, timestamp);
        this.connectionKey = connectionKey;
        this.bitfield = bitfield;
    }

    @Override
    public TorrentId getTorrentId() {
        return connectionKey.getTorrentId();
    }

    /**
     * @since 1.5
     */
    public Peer getPeer() {
        return connectionKey.getPeer();
    }

    /**
     * @since 1.9
     */
    public ConnectionKey getConnectionKey() {
        return connectionKey;
    }

    /**
     * @since 1.5
     */
    public Bitfield getBitfield() {
        return bitfield;
    }

    @NonNull
    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] id {" + getId() + "}, timestamp {" + getTimestamp() +
                "}, connection key {" + connectionKey + "}";
    }
}
