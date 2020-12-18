package threads.thor.bt.net.buffer;

import java.lang.ref.SoftReference;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.ReentrantLock;

import threads.thor.bt.Config;

public class BufferManager implements IBufferManager {

    private final int bufferSize;
    private final ConcurrentMap<Class<?>, Deque<SoftReference<?>>> releasedBuffers;

    public BufferManager(Config config) {
        this.bufferSize = config.getNetworkBufferSize();
        this.releasedBuffers = new ConcurrentHashMap<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    public BorrowedBuffer<ByteBuffer> borrowByteBuffer() {
        Deque<SoftReference<?>> deque = getReleasedBuffersDeque();
        SoftReference<ByteBuffer> ref;
        ByteBuffer buffer = null;
        do {
            ref = (SoftReference<ByteBuffer>) deque.pollLast();
            if (ref != null) {
                buffer = ref.get();
            }
            // check if the referenced buffer has been garbage collected
        } while (ref != null && buffer == null);

        if (buffer == null) {
            buffer = ByteBuffer.allocateDirect(bufferSize);
        } else {
            // reset buffer before re-using
            buffer.clear();
        }
        return new DefaultBorrowedBuffer<>(buffer);
    }

    private <T extends Buffer> Deque<SoftReference<?>> getReleasedBuffersDeque() {
        return releasedBuffers.computeIfAbsent(ByteBuffer.class, it -> new LinkedBlockingDeque<>());
    }

    private class DefaultBorrowedBuffer<T extends Buffer> implements BorrowedBuffer<T> {

        private final ReentrantLock lock;
        private volatile T buffer;

        DefaultBorrowedBuffer(T buffer) {
            this.buffer = Objects.requireNonNull(buffer);
            this.lock = new ReentrantLock();
        }

        @Override
        public T lockAndGet() {
            lock.lock();
            return buffer;
        }

        @Override
        public void unlock() {
            lock.unlock();
        }

        @Override
        public void release() {
            lock.lock();
            try {
                // check if lockAndGet() has been called by the current thread and not followed by unlock()
                /*
                if (lock.getHoldCount() > 1) {
                    throw new IllegalStateException("Buffer is locked and can't be released");
                }*/
                if (buffer != null) {
                    getReleasedBuffersDeque().add(new SoftReference<>(buffer));
                    buffer = null;
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
