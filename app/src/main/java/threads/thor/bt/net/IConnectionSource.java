package threads.thor.bt.net;

import java.util.concurrent.CompletableFuture;

import threads.thor.bt.metainfo.TorrentId;

public interface IConnectionSource {

    /**
     * Get connection for a given peer and threads.torrent ID.
     * If the connection does not exist yet, then an attempt to establish a new ougoing connection is made.
     * Blocks until the connection is established.
     *
     * @return Newly established or existing connection or, possibly, an error in the form of {@link ConnectionResult}
     * @since 1.6
     */
    ConnectionResult getConnection(Peer peer, TorrentId torrentId);

    /**
     * Get connection for a given peer and threads.torrent ID asynchronously.
     *
     * @return Future, which, when done, will contain a newly established or existing connection
     * or, possibly, an error in the form of {@link ConnectionResult}
     * @since 1.6
     */
    CompletableFuture<ConnectionResult> getConnectionAsync(Peer peer, TorrentId torrentId);
}
