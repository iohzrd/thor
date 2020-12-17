
package threads.thor.bt.torrent;

import java.util.function.IntPredicate;
import java.util.stream.IntStream;

public class ValidatingSelector implements PieceSelector {

    private final IntPredicate validator;
    private final PieceSelector delegate;

    /**
     * Creates a filtering selector.
     *
     * @param validator Filter
     * @param delegate  Delegate selector
     * @since 1.1
     */
    public ValidatingSelector(IntPredicate validator, PieceSelector delegate) {
        this.validator = validator;
        this.delegate = delegate;
    }

    @Override
    public IntStream getNextPieces(PieceStatistics pieceStatistics) {
        return delegate.getNextPieces(pieceStatistics)
                .filter(validator);
    }
}
