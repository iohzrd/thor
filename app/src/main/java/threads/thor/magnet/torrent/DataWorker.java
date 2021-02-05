package threads.thor.magnet.torrent;

import java.util.concurrent.CompletableFuture;

import threads.thor.magnet.metainfo.TorrentId;
import threads.thor.magnet.net.Peer;
import threads.thor.magnet.net.buffer.BufferedData;

public interface DataWorker {


    CompletableFuture<BlockRead> addBlockRequest(TorrentId torrentId, Peer peer, int pieceIndex, int offset, int length);

    CompletableFuture<BlockWrite> addBlock(TorrentId torrentId, int pieceIndex, int offset, BufferedData buffer);
}
