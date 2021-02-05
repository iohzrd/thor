package threads.thor.magnet.peer;

import threads.thor.magnet.metainfo.TorrentId;

public interface PeerSourceFactory {


    PeerSource getPeerSource(TorrentId torrentId);
}
