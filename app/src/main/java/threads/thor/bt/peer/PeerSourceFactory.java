package threads.thor.bt.peer;

import threads.thor.bt.metainfo.TorrentId;

public interface PeerSourceFactory {


    PeerSource getPeerSource(TorrentId torrentId);
}
