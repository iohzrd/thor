
package threads.thor.bt.torrent.data;

import threads.thor.bt.metainfo.TorrentId;

public interface BlockCache {

    BlockReader get(TorrentId torrentId, int pieceIndex, int offset, int length);
}
