package threads.thor.bt.net.buffer;

import java.nio.ByteBuffer;

/**
 * Provides the means to temporarily borrow a direct buffer.
 * <p>
 * It maintains a pool of unused buffers and for each borrow request decides whether it should:
 * - create and return a new buffer, if there are no more unused buffers left
 * - return an existing unused buffer, which has been returned to the pool by the previous borrower
 * <p>
 * After the borrower is done with the buffer, he should invoke
 * {@link BorrowedBuffer#release()} to return the buffer to the pool.
 *
 * @since 1.6
 */
public interface IBufferManager {

    /**
     * Temporarily borrow a direct byte buffer.
     * <p>
     * After the borrower is done with the buffer, he should invoke
     * {@link BorrowedBuffer#release()} to return the buffer to the pool.
     *
     * @since 1.6
     */
    BorrowedBuffer<ByteBuffer> borrowByteBuffer();
}
