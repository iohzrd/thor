package threads.thor.bt.event;

import androidx.annotation.NonNull;

import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentId;

public class MetadataAvailableEvent extends BaseEvent implements TorrentEvent {

    private final TorrentId torrentId;
    private final Torrent torrent;

    MetadataAvailableEvent(long id, long timestamp, TorrentId torrentId, Torrent torrent) {
        super(id, timestamp);
        this.torrentId = torrentId;
        this.torrent = torrent;
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId;
    }

    /**
     * @since 1.9
     */
    public Torrent getTorrent() {
        return torrent;
    }

    @NonNull
    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] id {" + getId() + "}, timestamp {" + getTimestamp() +
                "}, threads.torrent {" + torrentId + "}";
    }
}
