package threads.thor.magnet.net;

import java.util.Objects;

import threads.thor.magnet.metainfo.TorrentId;

public class ConnectionKey {
    private final Peer peer;
    private final int remotePort;
    private final TorrentId torrentId;

    public ConnectionKey(Peer peer, int remotePort, TorrentId torrentId) {
        Objects.requireNonNull(peer);
        Objects.requireNonNull(torrentId);
        this.peer = peer;
        this.remotePort = remotePort;
        this.torrentId = torrentId;
    }

    public Peer getPeer() {
        return peer;
    }

    public TorrentId getTorrentId() {
        return torrentId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ConnectionKey that = (ConnectionKey) obj;
        // must not use peer's port, because it can be updated
        return peer.getInetAddress().equals(that.peer.getInetAddress())
                && remotePort == that.remotePort
                && torrentId.equals(that.torrentId);

    }

    @Override
    public int hashCode() {
        int result = peer.getInetAddress().hashCode();
        result = 31 * result + remotePort;
        result = 31 * result + torrentId.hashCode();
        return result;
    }


}
