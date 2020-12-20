package threads.thor.bt.event;

import androidx.annotation.NonNull;

import threads.thor.bt.metainfo.TorrentId;

public class PieceVerifiedEvent extends BaseEvent {

    private final TorrentId torrentId;
    private final int pieceIndex;

    PieceVerifiedEvent(long id, long timestamp, TorrentId torrentId, int pieceIndex) {
        super(id, timestamp);
        this.torrentId = torrentId;
        this.pieceIndex = pieceIndex;
    }


    public int getPieceIndex() {
        return pieceIndex;
    }

    @NonNull
    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "]  threads.torrent {" + torrentId + "}, piece index {" + pieceIndex + "}";
    }
}
