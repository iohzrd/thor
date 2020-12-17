
package threads.thor.bt.torrent;

import threads.thor.bt.metainfo.TorrentId;

public interface BlockCache {

    BlockReader get(TorrentId torrentId, int pieceIndex, int offset, int length);
}
