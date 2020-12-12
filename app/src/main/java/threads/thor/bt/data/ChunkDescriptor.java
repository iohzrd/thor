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

package threads.thor.bt.data;

/**
 * Each threads.torrent is split into chunks (also called "pieces").
 *
 * <p>A chunk is a part of the threads.torrent's collection of files,
 * possibly overlapping several files (in case of multi-file torrents)
 * or being just a part of a single-file threads.torrent.
 *
 * <p>There is a SHA-1 checksum for each chunk in the threads.torrent's metainfo file,
 * so it's effectively an elementary unit of data in BitTorrent.
 * All chunks in a given threads.torrent have the same size
 * except for the last one, which can be smaller.
 *
 * <p>A typical chunk is usually too large to work with at I/O level.
 * So, for the needs of network transfer and storage each chunk is additionally split into "blocks".
 * Size of a block is quite an important parameter of threads.torrent messaging,
 * and it's usually client-specific (meaning that each client is free to choose the concrete value).
 *
 * @since 1.0
 */
public interface ChunkDescriptor extends BlockSet {

    /**
     * Expected hash of this chunk's contents as indicated in threads.torrent file.
     *
     * @return Expected hash of this chunk's contents; used to verify integrity of chunk's data
     * @since 1.2
     */
    byte[] getChecksum();

    /**
     * Get chunk's data accessor.
     *
     * @return Chunk's data accessor
     * @since 1.2
     */
    DataRange getData();
}
