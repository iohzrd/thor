package threads.thor.magnet.net.pipeline;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import threads.thor.magnet.net.buffer.BufferedData;

public class BufferedPieceRegistry {

    private final ConcurrentMap<Long, BufferedData> bufferMap;

    public BufferedPieceRegistry() {
        this.bufferMap = new ConcurrentHashMap<>();
        // TODO: Daemon cleaner
    }

    private static long zip(int pieceIndex, int offset) {
        return (((long) pieceIndex) << 32) + offset;
    }

    public boolean addBufferedPiece(int pieceIndex, int offset, BufferedData buffer) {
        if (pieceIndex < 0) {
            throw new IllegalArgumentException("Illegal piece index: " + pieceIndex);
        }
        Objects.requireNonNull(buffer);

        BufferedData existing = bufferMap.putIfAbsent(zip(pieceIndex, offset), buffer);
        return (existing == null);
    }

    public BufferedData getBufferedPiece(int pieceIndex, int offset) {
        return bufferMap.remove(zip(pieceIndex, offset));
    }
}
