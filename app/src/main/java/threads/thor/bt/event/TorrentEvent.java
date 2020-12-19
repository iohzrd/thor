package threads.thor.bt.event;

import threads.thor.bt.metainfo.TorrentId;

interface TorrentEvent {

    TorrentId getTorrentId();
}
