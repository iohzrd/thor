package threads.thor.bt.torrent;

import androidx.annotation.NonNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import threads.thor.bt.data.Bitfield;
import threads.thor.bt.data.Bitfield.PieceStatus;
import threads.thor.bt.net.ConnectionKey;

public final class PieceStatistics {

    private final Bitfield localBitfield;
    private final Map<ConnectionKey, Bitfield> peerBitFields;
    private final int[] pieceTotals;

    /**
     * Create statistics, based on the local peer's bitfield.
     *
     * @since 1.0
     */
    public PieceStatistics(Bitfield localBitfield) {
        this.localBitfield = localBitfield;
        this.peerBitFields = new ConcurrentHashMap<>();
        this.pieceTotals = new int[localBitfield.getPiecesTotal()];
    }

    /**
     * Add peer's bitfield.
     * For each piece, that the peer has, total count will be incremented by 1.
     *
     * @since 1.0
     */
    public void addBitfield(ConnectionKey connectionKey, @NonNull Bitfield bitfield) {
        validateBitfieldLength(bitfield);
        peerBitFields.put(connectionKey, bitfield);

        for (int i = 0; i < pieceTotals.length; i++) {
            if (bitfield.getPieceStatus(i) == PieceStatus.COMPLETE_VERIFIED) {
                incrementPieceTotal(i);
            }
        }
    }

    private synchronized void incrementPieceTotal(int i) {
        pieceTotals[i]++;
    }

    /**
     * Remove peer's bitfield.
     * For each piece, that the peer has, total count will be decremented by 1.
     *
     * @since 1.0
     */
    public void removeBitfield(ConnectionKey connectionKey) {
        Bitfield bitfield = peerBitFields.remove(connectionKey);
        if (bitfield == null) {
            return;
        }

        for (int i = 0; i < pieceTotals.length; i++) {
            if (bitfield.getPieceStatus(i) == PieceStatus.COMPLETE_VERIFIED) {
                decrementPieceTotal(i);
            }
        }
    }

    private synchronized void decrementPieceTotal(int i) {
        pieceTotals[i]--;
    }

    private void validateBitfieldLength(Bitfield bitfield) {
        if (bitfield.getPiecesTotal() != pieceTotals.length) {
            throw new IllegalArgumentException("Bitfield has invalid length (" + bitfield.getPiecesTotal() +
                    "). Expected number of pieces: " + pieceTotals.length);
        }
    }

    /**
     * Update peer's bitfield by indicating that the peer has a given piece.
     * Total count of the specified piece will be incremented by 1.
     *
     * @since 1.0
     */
    public void addPiece(ConnectionKey connectionKey, Integer pieceIndex) {
        Bitfield bitfield = peerBitFields.get(connectionKey);
        if (bitfield == null) {
            bitfield = new Bitfield(localBitfield.getPiecesTotal());
            Bitfield existing = peerBitFields.putIfAbsent(connectionKey, bitfield);
            if (existing != null) {
                bitfield = existing;
            }
        }

        markPieceVerified(bitfield, pieceIndex);
    }

    private synchronized void markPieceVerified(Bitfield bitfield, Integer pieceIndex) {
        if (!bitfield.isVerified(pieceIndex)) {
            bitfield.markVerified(pieceIndex);
            incrementPieceTotal(pieceIndex);
        }
    }


    public Optional<Bitfield> getPeerBitfield(ConnectionKey connectionKey) {
        return Optional.ofNullable(peerBitFields.get(connectionKey));
    }

    public synchronized int getCount(int pieceIndex) {
        return pieceTotals[pieceIndex];
    }

    public int getPiecesTotal() {
        return pieceTotals.length;
    }
}
