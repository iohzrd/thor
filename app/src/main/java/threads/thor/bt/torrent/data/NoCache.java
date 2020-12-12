/*
 * Copyright (c) 2016—2019 Andrei Tomashpolskiy and individual contributors.
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

package threads.thor.bt.torrent.data;

import threads.thor.bt.data.DataRange;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.torrent.TorrentRegistry;

public class NoCache implements BlockCache {

    private final TorrentRegistry torrentRegistry;


    public NoCache(TorrentRegistry torrentRegistry) {
        this.torrentRegistry = torrentRegistry;
    }

    @Override
    public BlockReader get(TorrentId torrentId, int pieceIndex, int offset, int length) {
        DataRange data = torrentRegistry.getDescriptor(torrentId).get()
                .getDataDescriptor()
                .getChunkDescriptors().get(pieceIndex)
                .getData();

        return buffer -> {
            int bufferRemaining = buffer.remaining();
            if (!data.getSubrange(offset, length)
                    .getBytes(buffer)) {
                throw new IllegalStateException("Failed to read data to buffer:" +
                        " piece index {" + pieceIndex + "}," +
                        " offset {" + offset + "}," +
                        " length: {" + length + "}," +
                        " buffer space {" + bufferRemaining + "}");
            }
            return true;
        };
    }
}
