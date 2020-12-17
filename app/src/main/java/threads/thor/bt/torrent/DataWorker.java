
package threads.thor.bt.torrent;

import java.util.concurrent.CompletableFuture;

import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.Peer;
import threads.thor.bt.net.buffer.BufferedData;

/**
 * Data worker is responsible for processing blocks and block requests, received from peers.
 *
 * @since 1.0
 */
public interface DataWorker {


    CompletableFuture<BlockRead> addBlockRequest(TorrentId torrentId, Peer peer, int pieceIndex, int offset, int length);

    /**
     * Add a write block request.
     *
     * @param torrentId  Torrent ID
     * @param peer       Peer, that the data has been received from
     * @param pieceIndex Index of the piece to write to (0-based)
     * @param offset     Offset in piece to start writing to (0-based)
     * @param buffer     Data
     * @return Future; rejected requests are returned immediately (see {@link BlockWrite#isRejected()})
     * @since 1.9
     */
    CompletableFuture<BlockWrite> addBlock(TorrentId torrentId, Peer peer, int pieceIndex, int offset, BufferedData buffer);
}
