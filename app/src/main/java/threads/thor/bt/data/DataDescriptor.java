package threads.thor.bt.data;

import java.io.Closeable;
import java.util.List;

import threads.thor.bt.metainfo.TorrentFile;

/**
 * Torrent's data descriptor.
 * Provides access to individual chunks and status of threads.torrent's data.
 *
 * @since 1.0
 */
public interface DataDescriptor extends Closeable {

    /**
     * @return List of chunks in the same order as they appear in threads.torrent's metainfo.
     * Hence, index of a chunk in this list can be used
     * as the index of the corresponding piece in data exchange between peers.
     * @since 1.0
     */
    List<ChunkDescriptor> getChunkDescriptors();

    /**
     * @return Status of torrent's data.
     * @since 1.0
     */
    Bitfield getBitfield();

    /**
     * Get a list of files that a given piece index intersects
     *
     * @return A list of files that a given piece index intersects
     * @since 1.7
     */
    List<TorrentFile> getFilesForPiece(int pieceIndex);

}
