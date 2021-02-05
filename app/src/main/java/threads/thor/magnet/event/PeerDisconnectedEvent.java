package threads.thor.magnet.event;

import androidx.annotation.NonNull;

import java.util.Objects;

import threads.thor.magnet.metainfo.TorrentId;
import threads.thor.magnet.net.ConnectionKey;

public class PeerDisconnectedEvent extends BaseEvent {

    private final ConnectionKey connectionKey;

    PeerDisconnectedEvent(long id, long timestamp, ConnectionKey connectionKey) {
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
        return "[" + this.getClass().getSimpleName() + "]  connection key {" + connectionKey + "}";
    }
}
