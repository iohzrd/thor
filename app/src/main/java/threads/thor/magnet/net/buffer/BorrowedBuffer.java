package threads.thor.magnet.net.buffer;

import java.nio.Buffer;

public interface BorrowedBuffer<T extends Buffer> {

    /**
     * Get the underlying buffer instance. It's strongly recommended to never
     * save the returned reference in object fields, variables or pass it via method parameters,
     * unless it is known for sure, that such field or variable will be short-lived
     * and used exclusively between calls to this method and {@link #unlock()}.
     * <p>
     * Caller of this method SHOULD call {@link #unlock()} as soon as he's finished working with the buffer,
     * e.g. by using the same try-finally pattern as when working with locks:
     *
     * <p>
     * This method will block the calling thread until the buffer is in UNLOCKED state.
     *
     * @return Buffer or null if the buffer has already been released
     * @since 1.6
     */
    T lockAndGet();

    /**
     * Unlock the buffer, thus allowing to {@link #release()} it.
     *
     * @throws IllegalMonitorStateException if the buffer is not locked or is locked by a different thread
     * @since 1.6
     */
    void unlock();

    /**
     * Release the underlying buffer.
     * <p>
     * The buffer will be returned to the pool of allocated but un-used buffers
     * and will eventually be garbage collected (releasing native memory in case of direct buffers)
     * or re-used in the form of another BorrowedBuffer instance.
     * <p>
     * This method will block the calling thread until the buffer is in UNLOCKED state.
     * <p>
     * This method has no effect, if the buffer has already been released.
     *
     * @since 1.6
     */
    void release();
}
