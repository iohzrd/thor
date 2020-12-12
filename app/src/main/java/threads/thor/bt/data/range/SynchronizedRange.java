/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package threads.thor.bt.data.range;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import threads.thor.bt.net.buffer.ByteBufferView;

/**
 * Data range synchronized with a shared lock.
 *
 * @since 1.2
 */
public class SynchronizedRange<T extends Range<T>> implements Range<T>, DelegatingRange<T> {

    private final Range<T> delegate;

    /**
     * Shared lock for this range and all its' child subranges
     */
    private final ReadWriteLock lock;

    /**
     * Create a data range synchronized with a private lock.
     *
     * @since 1.2
     */
    public SynchronizedRange(Range<T> delegate) {
        this.delegate = delegate;
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * Create a data range synchronized with a shared lock.
     *
     * @since 1.2
     */
    private SynchronizedRange(Range<T> delegate, ReadWriteLock lock) {
        this.delegate = delegate;
        this.lock = lock;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.2
     */
    @Override
    public long length() {
        return delegate.length();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Child subrange shares the same lock as its' parent range.
     *
     * @since 1.2
     */
    @Override
    public SynchronizedRange<T> getSubrange(long offset, long length) {
        return new SynchronizedRange<>(delegate.getSubrange(offset, length), lock);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Child subrange shares the same lock as its' parent range.
     *
     * @since 1.2
     */
    @Override
    public SynchronizedRange<T> getSubrange(long offset) {
        return new SynchronizedRange<>(delegate.getSubrange(offset), lock);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Blocks current thread if there are concurrent write operations in progress.
     * Blocks all concurrent write operations.
     *
     * @since 1.2
     */
    @Override
    public byte[] getBytes() {
        lock.readLock().lock();
        try {
            return delegate.getBytes();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean getBytes(ByteBuffer buffer) {
        lock.readLock().lock();
        try {
            return delegate.getBytes(buffer);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Blocks current thread if there are concurrent read or write operations in progress.
     * Block all concurrent read or write operations.
     *
     * @since 1.2
     */
    @Override
    public void putBytes(byte[] block) {
        lock.writeLock().lock();
        try {
            delegate.putBytes(block);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void putBytes(ByteBufferView buffer) {
        lock.writeLock().lock();
        try {
            delegate.putBytes(buffer);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * @since 1.3
     */
    ReadWriteLock getLock() {
        return lock;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T getDelegate() {
        return (T) delegate;
    }
}
