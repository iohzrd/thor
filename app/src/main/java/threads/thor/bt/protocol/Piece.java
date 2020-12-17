package threads.thor.bt.protocol;

import java.nio.ByteBuffer;
import java.util.Objects;

import threads.thor.bt.torrent.BlockReader;

public final class Piece implements Message {

    private final int pieceIndex;
    private final int offset;
    private final int length;
    private final BlockReader reader;

    // TODO: using BlockReader here is sloppy... just temporary
    public Piece(int pieceIndex, int offset, int length, BlockReader reader) throws InvalidMessageException {
        if (pieceIndex < 0 || offset < 0 || length <= 0) {
            throw new InvalidMessageException("Invalid arguments: piece index (" +
                    pieceIndex + "), offset (" + offset + "), block length (" + length + ")");
        }
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.length = length;
        this.reader = reader;
    }

    // TODO: Temporary (used only for incoming pieces)
    public Piece(int pieceIndex, int offset, int length) throws InvalidMessageException {
        if (pieceIndex < 0 || offset < 0 || length <= 0) {
            throw new InvalidMessageException("Invalid arguments: piece index (" +
                    pieceIndex + "), offset (" + offset + "), block length (" + length + ")");
        }
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.length = length;
        this.reader = null;
    }

    /**
     * @since 1.0
     */
    public int getPieceIndex() {
        return pieceIndex;
    }

    /**
     * @since 1.0
     */
    public int getOffset() {
        return offset;
    }

    /**
     * @since 1.9
     */
    public int getLength() {
        return length;
    }

    public boolean writeBlockTo(ByteBuffer buffer) {
        Objects.requireNonNull(reader);
        return reader.readTo(buffer);
    }


    @Override
    public Integer getMessageId() {
        return StandardBittorrentProtocol.PIECE_ID;
    }
}
