package threads.thor.bt.event;

import androidx.annotation.NonNull;

import java.util.Objects;

import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.Peer;

public class PeerDiscoveredEvent extends BaseEvent implements TorrentEvent {

    private final TorrentId torrentId;
    private final Peer peer;

    PeerDiscoveredEvent(long id, long timestamp, TorrentId torrentId, Peer peer) {
        super(id, timestamp);
        this.torrentId = Objects.requireNonNull(torrentId);
        this.peer = Objects.requireNonNull(peer);
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId;
    }

    /**
     * @since 1.5
     */
    public Peer getPeer() {
        return peer;
    }

    @NonNull
    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "]  threads.torrent {" + torrentId + "}, peer {" + peer + "}";
    }
}
