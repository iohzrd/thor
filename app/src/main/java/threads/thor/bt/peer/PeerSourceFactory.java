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

package threads.thor.bt.peer;

import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentId;

/**
 * Factory of peer sources.
 * <p>
 * Can be used to provide peer sources that operate on a per-threads.torrent basis
 * or need access to DI services.
 *
 * @since 1.0
 */
public interface PeerSourceFactory {

    /**
     * Create a peer source for a given threads.torrent.
     * Implementations are free to return the same instance for all torrents.
     *
     * @since 1.0
     * @deprecated since 1.3 in favor of {@link #getPeerSource(TorrentId)}
     */
    default PeerSource getPeerSource(Torrent torrent) {
        return getPeerSource(torrent.getTorrentId());
    }

    /**
     * Create a peer source for a given threads.torrent.
     * Implementations are free to return the same instance for all torrents.
     *
     * @since 1.3
     */
    PeerSource getPeerSource(TorrentId torrentId);
}
