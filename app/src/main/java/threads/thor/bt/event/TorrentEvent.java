package threads.thor.bt.event;

import threads.thor.bt.metainfo.TorrentId;

interface TorrentEvent extends Event {

    /**
     * @since 1.5
     */
    TorrentId getTorrentId();
}
