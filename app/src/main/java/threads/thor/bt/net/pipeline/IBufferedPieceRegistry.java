package threads.thor.bt.net.pipeline;

import threads.thor.bt.net.buffer.BufferedData;

public interface IBufferedPieceRegistry {


    boolean addBufferedPiece(int pieceIndex, int offset, BufferedData buffer);

    BufferedData getBufferedPiece(int pieceIndex, int offset);
}
