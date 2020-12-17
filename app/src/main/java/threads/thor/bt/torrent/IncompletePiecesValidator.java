
package threads.thor.bt.torrent;

import java.util.function.IntPredicate;

import threads.thor.bt.data.Bitfield;

public class IncompletePiecesValidator implements IntPredicate {

    private final Bitfield bitfield;

    public IncompletePiecesValidator(Bitfield bitfield) {
        this.bitfield = bitfield;
    }

    @Override
    public boolean test(int pieceIndex) {
        return !isComplete(pieceIndex);
    }

    private boolean isComplete(int pieceIndex) {
        Bitfield.PieceStatus pieceStatus = bitfield.getPieceStatus(pieceIndex);
        return pieceStatus == Bitfield.PieceStatus.COMPLETE || pieceStatus == Bitfield.PieceStatus.COMPLETE_VERIFIED;
    }
}
