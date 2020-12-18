package threads.thor.bt.event;

import androidx.annotation.NonNull;

import threads.thor.bt.data.Bitfield;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.ConnectionKey;


public class PeerBitfieldUpdatedEvent extends BaseEvent implements TorrentEvent {

    private final ConnectionKey connectionKey;
    private final Bitfield bitfield;

    PeerBitfieldUpdatedEvent(long id, long timestamp,
                             ConnectionKey connectionKey, Bitfield bitfield) {
        super(id, timestamp);
        this.connectionKey = connectionKey;
        this.bitfield = bitfield;
    }

    @Override
    public TorrentId getTorrentId() {
        return connectionKey.getTorrentId();
    }

    /**
     * @since 1.9
     */
    public ConnectionKey getConnectionKey() {
        return connectionKey;
    }

    /**
     * @since 1.5
     */
    public Bitfield getBitfield() {
        return bitfield;
    }

    @NonNull
    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] id {" + getId() + "}, timestamp {" + getTimestamp() +
                "}, connection key {" + connectionKey + "}";
    }
}
