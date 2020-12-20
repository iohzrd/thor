package threads.thor.bt.torrent;

import java.util.concurrent.CompletableFuture;

import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.Peer;
import threads.thor.bt.net.buffer.BufferedData;

public interface DataWorker {


    CompletableFuture<BlockRead> addBlockRequest(TorrentId torrentId, Peer peer, int pieceIndex, int offset, int length);

    CompletableFuture<BlockWrite> addBlock(TorrentId torrentId, int pieceIndex, int offset, BufferedData buffer);
}
