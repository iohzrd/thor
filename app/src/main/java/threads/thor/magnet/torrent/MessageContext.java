package threads.thor.magnet.torrent;

import threads.thor.magnet.metainfo.TorrentId;
import threads.thor.magnet.net.ConnectionKey;
import threads.thor.magnet.net.Peer;

public class MessageContext {

    private final ConnectionState connectionState;
    private final ConnectionKey connectionKey;

    MessageContext(ConnectionKey connectionKey, ConnectionState connectionState) {
        this.connectionKey = connectionKey;
        this.connectionState = connectionState;
    }

    /**
     * @return Optional threads.torrent ID or empty if not applicable
     * (e.g. if a message was received outside of a threads.torrent processing session)
     * @since 1.0
     */
    public TorrentId getTorrentId() {
        return connectionKey.getTorrentId();
    }

    /**
     * @return Remote peer
     * @since 1.0
     */
    public Peer getPeer() {
        return connectionKey.getPeer();
    }

    /**
     * @return Current state of the connection
     * @since 1.0
     */
    public ConnectionState getConnectionState() {
        return connectionState;
    }

    /**
     * @since 1.9
     */
    public ConnectionKey getConnectionKey() {
        return connectionKey;
    }
}
