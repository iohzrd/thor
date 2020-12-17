package threads.thor.bt.torrent;

import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

public abstract class BaseStreamSelector implements PieceSelector {

    @Override
    public final IntStream getNextPieces(PieceStatistics pieceStatistics) {
        return StreamSupport.intStream(Spliterators.spliteratorUnknownSize(createIterator(pieceStatistics),
                characteristics()), false);
    }

    /**
     * Select pieces based on the provided statistics.
     *
     * @return Stream of piece indices in the form of Integer iterator
     * @since 1.1
     */
    protected abstract PrimitiveIterator.OfInt createIterator(PieceStatistics pieceStatistics);

    private int characteristics() {
        return Spliterator.NONNULL | Spliterator.DISTINCT | Spliterator.ORDERED;
    }
}
