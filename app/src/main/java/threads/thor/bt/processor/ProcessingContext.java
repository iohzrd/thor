package threads.thor.bt.processor;

import androidx.annotation.Nullable;

import java.util.Optional;

import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.torrent.TorrentSessionState;

public interface ProcessingContext {

    @Nullable
    TorrentId getTorrentId();

    @Nullable
    Torrent getTorrent();


    Optional<TorrentSessionState> getState();
}
