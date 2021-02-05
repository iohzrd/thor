package threads.thor.magnet.data;

import java.util.BitSet;
import java.util.concurrent.locks.ReentrantLock;

import threads.thor.magnet.protocol.BitOrder;
import threads.thor.magnet.protocol.Protocols;

public class Bitfield {

    // TODO: use EMPTY and PARTIAL instead of INCOMPLETE
    /**
     * Bitmask indicating availability of pieces.
     * If the n-th bit is set, then the n-th piece is complete and verified.
     */
    private final BitSet bitmask;
    /**
     * Total number of pieces in torrent.
     */
    private final int piecesTotal;

    private final ReentrantLock lock;
    /**
     * Bitmask indicating pieces that should be skipped.
     * If the n-th bit is set, then the n-th piece should be skipped.
     */
    private volatile /*nullable*/ BitSet skipped;


    /**
     * Creates empty bitfield.
     * Useful when peer does not communicate its' bitfield (e.g. when he has no data).
     *
     * @param piecesTotal Total number of pieces in threads.torrent.
     * @since 1.0
     */
    public Bitfield(int piecesTotal) {
        this.piecesTotal = piecesTotal;
        this.bitmask = new BitSet(piecesTotal);
        this.lock = new ReentrantLock();
    }


    /**
     * Creates bitfield based on a bitmask.
     * Used for creating peers' bitfields.
     *
     * @param value       Bitmask that describes status of all pieces.
     *                    If position i is set to 1, then piece with index i is complete and verified.
     * @param piecesTotal Total number of pieces in threads.torrent.
     * @since 1.7
     */
    public Bitfield(byte[] value, BitOrder bitOrder, int piecesTotal) {
        this.piecesTotal = piecesTotal;
        this.bitmask = createBitmask(value, bitOrder, piecesTotal);
        this.lock = new ReentrantLock();
    }

    private static BitSet createBitmask(byte[] bytes, BitOrder bitOrder, int piecesTotal) {
        int expectedBitmaskLength = getBitmaskLength(piecesTotal);
        if (bytes.length != expectedBitmaskLength) {
            throw new IllegalArgumentException("Invalid bitfield: total (" + piecesTotal +
                    "), bitmask length (" + bytes.length + "). Expected bitmask length: " + expectedBitmaskLength);
        }

        if (bitOrder == BitOrder.LITTLE_ENDIAN) {
            bytes = Protocols.reverseBits(bytes);
        }

        BitSet bitmask = new BitSet(piecesTotal);
        for (int i = 0; i < piecesTotal; i++) {
            if (Protocols.isSet(bytes, BitOrder.BIG_ENDIAN, i)) {
                bitmask.set(i);
            }
        }
        return bitmask;
    }

    private static int getBitmaskLength(int piecesTotal) {
        return (int) Math.ceil(piecesTotal / 8d);
    }

    /**
     * @return Bitmask that describes status of all pieces.
     * If the n-th bit is set, then the n-th piece
     * is in {@link PieceStatus#COMPLETE_VERIFIED} status.
     * @since 1.7
     */
    public BitSet getBitmask() {
        lock.lock();
        try {
            return Protocols.copyOf(bitmask);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @param bitOrder Order of bits to use to create the byte array
     * @return Bitmask that describes status of all pieces.
     * If the n-th bit is set, then the n-th piece
     * is in {@link PieceStatus#COMPLETE_VERIFIED} status.
     * @since 1.7
     */
    public byte[] toByteArray(BitOrder bitOrder) {
        byte[] bytes;
        boolean truncated;

        lock.lock();
        try {
            bytes = bitmask.toByteArray();
            truncated = (bitmask.length() < piecesTotal);
        } finally {
            lock.unlock();
        }

        if (bitOrder == BitOrder.LITTLE_ENDIAN) {
            bytes = Protocols.reverseBits(bytes);
        }
        if (truncated) {
            byte[] arr = new byte[getBitmaskLength(piecesTotal)];
            System.arraycopy(bytes, 0, arr, 0, bytes.length);
            return arr;
        } else {
            return bytes;
        }
    }

    /**
     * @return Total number of pieces in threads.torrent.
     * @since 1.0
     */
    public int getPiecesTotal() {
        return piecesTotal;
    }

    /**
     * @return Number of pieces that have status {@link PieceStatus#COMPLETE_VERIFIED}.
     * @since 1.0
     */
    public int getPiecesComplete() {
        lock.lock();
        try {
            return bitmask.cardinality();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return Number of pieces that have status different {@link PieceStatus#COMPLETE_VERIFIED}.
     * @since 1.7
     */
    public int getPiecesIncomplete() {
        lock.lock();
        try {
            return getPiecesTotal() - bitmask.cardinality();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return Number of pieces that have status different from {@link PieceStatus#COMPLETE_VERIFIED}
     * and should NOT be skipped.
     * @since 1.0
     */
    public int getPiecesRemaining() {
        lock.lock();
        try {
            if (skipped == null) {
                return getPiecesTotal() - getPiecesComplete();
            } else {
                BitSet bitmask = getBitmask();
                bitmask.or(skipped);
                return getPiecesTotal() - bitmask.cardinality();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * @param pieceIndex Piece index (0-based)
     * @return Status of the corresponding piece.
     * @see DataDescriptor#getChunkDescriptors()
     * @since 1.0
     */
    public PieceStatus getPieceStatus(int pieceIndex) {
        validatePieceIndex(pieceIndex);

        PieceStatus status;

        boolean verified;
        lock.lock();
        try {
            verified = this.bitmask.get(pieceIndex);
        } finally {
            lock.unlock();
        }

        if (verified) {
            status = PieceStatus.COMPLETE_VERIFIED;
        } else {
            status = PieceStatus.INCOMPLETE;
        }

        return status;
    }

    /**
     * Shortcut method to find out if the piece has been downloaded.
     *
     * @param pieceIndex Piece index (0-based)
     * @return true if the piece has been downloaded
     * @since 1.1
     */
    public boolean isComplete(int pieceIndex) {
        PieceStatus pieceStatus = getPieceStatus(pieceIndex);
        return (pieceStatus == PieceStatus.COMPLETE || pieceStatus == PieceStatus.COMPLETE_VERIFIED);
    }

    /**
     * Shortcut method to find out if the piece has been downloaded and verified.
     *
     * @param pieceIndex Piece index (0-based)
     * @return true if the piece has been downloaded and verified
     * @since 1.1
     */
    public boolean isVerified(int pieceIndex) {
        PieceStatus pieceStatus = getPieceStatus(pieceIndex);
        return pieceStatus == PieceStatus.COMPLETE_VERIFIED;
    }

    /**
     * Mark piece as complete and verified.
     *
     * @param pieceIndex Piece index (0-based)
     * @see DataDescriptor#getChunkDescriptors()
     * @since 1.0
     */
    public void markVerified(int pieceIndex) {
        assertChunkComplete(pieceIndex);

        lock.lock();
        try {
            bitmask.set(pieceIndex);
        } finally {
            lock.unlock();
        }
    }

    private void assertChunkComplete(int pieceIndex) {
        validatePieceIndex(pieceIndex);
    }

    private void validatePieceIndex(Integer pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= getPiecesTotal()) {
            throw new RuntimeException("Illegal piece index: " + pieceIndex +
                    ", expected 0.." + (getPiecesTotal() - 1));
        }
    }

    /**
     * Mark a piece as skipped
     *
     * @since 1.7
     */
    public void skip(int pieceIndex) {
        validatePieceIndex(pieceIndex);

        lock.lock();
        try {
            if (skipped == null) {
                skipped = new BitSet(getPiecesTotal());
            }
            skipped.set(pieceIndex);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Status of a particular piece.
     *
     * @since 1.0
     */
    public enum PieceStatus {
        /*EMPTY, PARTIAL,*/INCOMPLETE, COMPLETE, COMPLETE_VERIFIED
    }
}
