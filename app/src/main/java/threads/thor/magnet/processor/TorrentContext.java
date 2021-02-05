package threads.thor.magnet.processor;

import androidx.annotation.Nullable;

import threads.thor.magnet.data.Bitfield;
import threads.thor.magnet.data.Storage;
import threads.thor.magnet.metainfo.Torrent;
import threads.thor.magnet.torrent.Assignments;
import threads.thor.magnet.torrent.MessageRouter;
import threads.thor.magnet.torrent.PieceSelector;
import threads.thor.magnet.torrent.PieceStatistics;
import threads.thor.magnet.torrent.TorrentSessionState;

public abstract class TorrentContext {

    private final PieceSelector pieceSelector;
    private final Storage storage;

    private volatile Torrent torrent;
    private volatile TorrentSessionState state;
    private volatile MessageRouter router;
    private volatile Bitfield bitfield;
    private volatile Assignments assignments;
    private volatile PieceStatistics pieceStatistics;


    public TorrentContext(PieceSelector pieceSelector,
                          Storage storage) {
        this.pieceSelector = pieceSelector;
        this.storage = storage;
    }


    public PieceSelector getPieceSelector() {
        return pieceSelector;
    }


    public Storage getStorage() {
        return storage;
    }


    @Nullable
    public Torrent getTorrent() {
        return torrent;
    }

    public void setTorrent(Torrent torrent) {
        this.torrent = torrent;
    }

    @Nullable
    public TorrentSessionState getState() {
        return state;
    }

    public void setState(TorrentSessionState state) {
        this.state = state;
    }

    public MessageRouter getRouter() {
        return router;
    }

    public void setRouter(MessageRouter router) {
        this.router = router;
    }

    public Bitfield getBitfield() {
        return bitfield;
    }

    public void setBitfield(Bitfield bitfield) {
        this.bitfield = bitfield;
    }

    public Assignments getAssignments() {
        return assignments;
    }

    public void setAssignments(Assignments assignments) {
        this.assignments = assignments;
    }

    public PieceStatistics getPieceStatistics() {
        return pieceStatistics;
    }

    public void setPieceStatistics(PieceStatistics pieceStatistics) {
        this.pieceStatistics = pieceStatistics;
    }

}
