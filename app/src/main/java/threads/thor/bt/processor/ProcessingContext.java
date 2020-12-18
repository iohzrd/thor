package threads.thor.bt.processor;

import androidx.annotation.Nullable;

import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.torrent.TorrentSessionState;

public interface ProcessingContext {

    @Nullable
    TorrentId getTorrentId();

    @Nullable
    Torrent getTorrent();

    @Nullable
    TorrentSessionState getState();
}
