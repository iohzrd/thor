package threads.thor.bt.torrent;

import java.util.stream.IntStream;

public interface PieceSelector {

    /**
     * Select pieces based on the provided statistics.
     *
     * @return Stream of selected piece indices
     * @since 1.1
     */
    IntStream getNextPieces(PieceStatistics pieceStatistics);
}
