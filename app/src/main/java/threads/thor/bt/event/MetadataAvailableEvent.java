package threads.thor.bt.event;

import androidx.annotation.NonNull;

import threads.thor.bt.metainfo.TorrentId;

public class MetadataAvailableEvent extends BaseEvent implements TorrentEvent {

    private final TorrentId torrentId;

    MetadataAvailableEvent(long id, long timestamp, TorrentId torrentId) {
        super(id, timestamp);
        this.torrentId = torrentId;
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId;
    }

    @NonNull
    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] id {" + getId() + "}, timestamp {" + getTimestamp() +
                "}, threads.torrent {" + torrentId + "}";
    }
}
