package threads.thor.bt.data;

import java.nio.channels.ReadableByteChannel;

public interface DataReader {

    /**
     * Create a sequential view of threads.torrent's data in the form of a {@link ReadableByteChannel}.
     * <p>
     * The returned channel's {@code read(ByteBuffer)} method has the following behavior:
     *
     * <ul>
     * <li>blocks, until at least one byte of data has been read</li>
     * <li>returns -1, when all data has been processed.</li>
     * </ul>
     * <p>
     * Code snippet:
     *
     * <pre>
     * ByteBuffer data = ByteBuffer.allocate(TORRENT_SIZE);
     * ByteBuffer buffer = ByteBuffer.allocate(8192);
     * ReadableByteChannel ch = reader.createChannel();
     *
     * while (ch.read(buffer) &gt;= 0) {
     *     buffer.flip();
     *     data.put(buffer);
     *     buffer.clear();
     * }
     * </pre>
     *
     * @return Channel-like sequential view of threads.torrent's data.
     * The returned channel will block on read() calls,
     * if the next portion of data is not yet available.
     * @since 1.8
     */
    ReadableByteChannel createChannel();
}
