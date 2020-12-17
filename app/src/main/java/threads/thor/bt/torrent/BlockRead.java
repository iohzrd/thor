package threads.thor.bt.torrent;

import java.util.Optional;

import threads.thor.bt.net.Peer;

/**
 * Read block command.
 * <p>
 * If {@link #isRejected()} returns true,
 * this means that the request was not accepted by the data worker.
 * If {@link #getError()} is not empty,
 * this means that an exception happened during the request processing.
 * Subsequently, {@link #getReader()} will return {@link Optional#empty()} in both cases.
 *
 * @since 1.0
 */
public class BlockRead {

    private final Peer peer;
    private final int pieceIndex;
    private final int offset;
    private final int length;
    private final BlockReader reader;
    private final boolean rejected;
    private final Throwable error;

    private BlockRead(Peer peer, Throwable error, boolean rejected,
                      int pieceIndex, int offset, int length, BlockReader reader) {
        this.peer = peer;
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
    static BlockRead ready(Peer peer, int pieceIndex, int offset, int length, BlockReader reader) {
        return new BlockRead(peer, null, false, pieceIndex, offset, length, reader);
    }

    /**
     * @since 1.0
     */
    static BlockRead rejected(Peer peer, int pieceIndex, int offset, int length) {
        return new BlockRead(peer, null, true, pieceIndex, offset, length, null);
    }

    /**
     * @since 1.0
     */
    static BlockRead exceptional(Peer peer, Throwable error, int pieceIndex, int offset, int length) {
        return new BlockRead(peer, error, false, pieceIndex, offset, length, null);
    }

    /**
     * @return Requesting peer
     * @since 1.0
     */
    public Peer getPeer() {
        return peer;
    }

    /**
     * @return true if the request was not accepted by the data worker
     * @since 1.0
     */
    public boolean isRejected() {
        return rejected;
    }

    /**
     * @return Index of the piece being requested
     * @since 1.0
     */
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
