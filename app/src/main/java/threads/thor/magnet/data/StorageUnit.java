package threads.thor.magnet.data;

import java.io.Closeable;
import java.nio.Buffer;
import java.nio.ByteBuffer;

import threads.thor.magnet.net.buffer.ByteBufferView;

public interface StorageUnit extends Closeable {

    /**
     * Try to read a block of data into the provided buffer, starting with a given offset.
     * Maximum number of bytes to be read is determined by {@link Buffer#remaining()}.
     * <p>Storage must throw an exception if
     * <blockquote>
     * <code>offset &gt; {@link #capacity()} - buffer.remaining()</code>
     * </blockquote>
     *
     * @param buffer Buffer to read bytes into.
     *               Value returned by <b>buffer.remaining()</b> determines
     *               the maximum number of bytes to read.
     * @param offset Index to start reading from (0-based)
     * @return Actual number of bytes read
     * @since 1.0
     */
    int readBlock(ByteBuffer buffer, long offset);

    /**
     * @since 1.9
     */
    void readBlockFully(ByteBuffer buffer, long offset);

    /**
     * Try to read a block of data into the provided array, starting with a given offset.
     * Maximum number of bytes to be read is determined by {@link Buffer#remaining()}.
     * <p>Storage must throw an exception if
     * <blockquote>
     * <code>offset &gt; {@link #capacity()} - length</code>
     * </blockquote>
     *
     * @param buffer Array to read bytes into.
     *               Array's length determines the maximum number of bytes to read.
     * @param offset Index to starting reading from (0-based)
     * @return Actual number of bytes read
     * @since 1.0
     */
    default int readBlock(byte[] buffer, long offset) {
        return readBlock(ByteBuffer.wrap(buffer), offset);
    }


    /**
     * @since 1.9
     */
    void writeBlockFully(ByteBuffer buffer, long offset);


    /**
     * @since 1.9
     */
    void writeBlockFully(ByteBufferView buffer, long offset);

    /**
     * Get total maximum capacity of this storage.
     *
     * @return Total maximum capacity of this storage
     * @since 1.0
     */
    long capacity();

    /**
     * Get current amount of data in this storage.
     *
     * @return Current amount of data in this storage
     * @since 1.1
     */
    long size();
}
