package threads.thor.bt.processor;

import androidx.annotation.Nullable;

import java.util.Optional;

import threads.thor.bt.data.Bitfield;
import threads.thor.bt.data.Storage;
import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.torrent.PieceStatistics;
import threads.thor.bt.torrent.TorrentSessionState;
import threads.thor.bt.torrent.messaging.Assignments;
import threads.thor.bt.torrent.messaging.MessageRouter;
import threads.thor.bt.torrent.selector.PieceSelector;

public abstract class TorrentContext implements ProcessingContext {

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


    @Override
    @Nullable
    public Torrent getTorrent() {
        return torrent;
    }

    public void setTorrent(Torrent torrent) {
        this.torrent = torrent;
    }

    @Override
    public Optional<TorrentSessionState> getState() {
        return Optional.ofNullable(state);
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
