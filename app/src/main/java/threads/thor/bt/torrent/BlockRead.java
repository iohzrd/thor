package threads.thor.bt.torrent;

import java.util.Optional;

public class BlockRead {


    private final int pieceIndex;
    private final int offset;
    private final int length;
    private final BlockReader reader;
    private final boolean rejected;
    private final Throwable error;

    private BlockRead(Throwable error, boolean rejected,
                      int pieceIndex, int offset, int length, BlockReader reader) {

        this.error = error;
        this.rejected = rejected;
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.length = length;
        this.reader = reader;
    }

    /**
     * @since 1.9
     */
    static BlockRead ready(int pieceIndex, int offset, int length, BlockReader reader) {
        return new BlockRead(null, false, pieceIndex, offset, length, reader);
    }

    /**
     * @since 1.0
     */
    static BlockRead rejected(int pieceIndex, int offset, int length) {
        return new BlockRead(null, true, pieceIndex, offset, length, null);
    }

    /**
     * @since 1.0
     */
    static BlockRead exceptional(Throwable error, int pieceIndex, int offset, int length) {
        return new BlockRead(error, false, pieceIndex, offset, length, null);
    }


    public boolean isRejected() {
        return rejected;
    }


    public int getPieceIndex() {
        return pieceIndex;
    }

    /**
     * @return Offset in a piece to read the block from
     * @since 1.0
     */
    public int getOffset() {
        return offset;
    }

    /**
     * @return Block length
     * @since 1.9
     */
    public int getLength() {
        return length;
    }

    /**
     * @return Block reader or {@link Optional#empty()},
     * if {@link #isRejected()} returns true or if {@link #getError()} is not empty
     * @since 1.9
     */
    public Optional<BlockReader> getReader() {
        return Optional.ofNullable(reader);
    }

    /**
     * @return {@link Optional#empty()} if processing of the request completed normally,
     * or exception otherwise.
     * @since 1.0
     */
    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }
}
