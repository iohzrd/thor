package threads.thor.bt.event;

import androidx.annotation.NonNull;

import java.util.Objects;

import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.ConnectionKey;

public class PeerConnectedEvent extends BaseEvent {

    private final ConnectionKey connectionKey;

    PeerConnectedEvent(long id, long timestamp, ConnectionKey connectionKey) {
        super(id, timestamp);
        this.connectionKey = Objects.requireNonNull(connectionKey);
    }


    public TorrentId getTorrentId() {
        return connectionKey.getTorrentId();
    }

    /**
     * @since 1.9
     */
    public ConnectionKey getConnectionKey() {
        return connectionKey;
    }

    @NonNull
    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] connection key {" + connectionKey + "}";
    }
}
