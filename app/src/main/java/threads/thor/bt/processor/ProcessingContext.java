package threads.thor.bt.processor;

import java.util.Optional;

import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.torrent.TorrentSessionState;

public interface ProcessingContext {


    TorrentId getTorrentId();


    Optional<Torrent> getTorrent();


    Optional<TorrentSessionState> getState();
}
