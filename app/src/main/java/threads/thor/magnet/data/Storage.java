package threads.thor.magnet.data;

import threads.thor.magnet.metainfo.Torrent;
import threads.thor.magnet.metainfo.TorrentFile;

public interface Storage {

    /**
     * Get a storage unit for a particular threads.torrent file.
     *
     * @param torrent     Torrent metainfo
     * @param torrentFile Torrent file metainfo
     * @return Storage unit for a single threads.torrent file
     * @since 1.0
     */
    StorageUnit getUnit(Torrent torrent, TorrentFile torrentFile);

}