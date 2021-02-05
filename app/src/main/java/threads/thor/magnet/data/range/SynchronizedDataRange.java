package threads.thor.magnet.data.range;

import java.nio.ByteBuffer;
import java.util.function.Function;

import threads.thor.magnet.data.DataRange;
import threads.thor.magnet.data.DataRangeVisitor;
import threads.thor.magnet.net.buffer.ByteBufferView;

public class SynchronizedDataRange<T extends Range<T>> implements DataRange, DelegatingRange<T> {

    private final SynchronizedRange<T> delegate;
    private final Function<T, DataRange> converter;

    public SynchronizedDataRange(SynchronizedRange<T> delegate, Function<T, DataRange> converter) {
        this.delegate = delegate;
        this.converter = converter;
    }

    @Override
    public void visitUnits(DataRangeVisitor visitor) {
        delegate.getLock().writeLock().lock();
        try {
            converter.apply(delegate.getDelegate()).visitUnits(visitor);
        } finally {
            delegate.getLock().writeLock().unlock();
        }
    }

    @Override
    public long length() {
        return delegate.length();
    }

    @Override
    public DataRange getSubrange(long offset, long length) {
        return new SynchronizedDataRange<>(delegate.getSubrange(offset, length), converter);
    }

    @Override
    public DataRange getSubrange(long offset) {
        return new SynchronizedDataRange<>(delegate.getSubrange(offset), converter);
    }

    @Override
    public byte[] getBytes() {
        return delegate.getBytes();
    }

    @Override
    public boolean getBytes(ByteBuffer buffer) {
        return delegate.getBytes(buffer);
    }

    @Override
    public void putBytes(byte[] block) {
        delegate.putBytes(block);
    }

    @Override
    public void putBytes(ByteBufferView buffer) {
        delegate.putBytes(buffer);
    }

    @Override
    public T getDelegate() {
        return delegate.getDelegate();
    }
}
