package threads.thor.bt.net.buffer;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class DelegatingByteBufferView implements ByteBufferView {

    private final ByteBuffer delegate;

    public DelegatingByteBufferView(ByteBuffer delegate) {
        this.delegate = delegate;
    }

    @Override
    public int position() {
        return delegate.position();
    }

    @Override
    public ByteBufferView position(int newPosition) {
        delegate.position(newPosition);
        return this;
    }

    @Override
    public int limit() {
        return delegate.limit();
    }

    @Override
    public ByteBufferView limit(int newLimit) {
        delegate.limit(newLimit);
        return this;
    }

    @Override
    public int capacity() {
        return delegate.capacity();
    }

    @Override
    public boolean hasRemaining() {
        return delegate.hasRemaining();
    }

    @Override
    public int remaining() {
        return delegate.remaining();
    }

    @Override
    public byte get() {
        return delegate.get();
    }

    @Override
    public short getShort() {
        return delegate.getShort();
    }

    @Override
    public int getInt() {
        return delegate.getInt();
    }

    @Override
    public ByteBufferView get(byte[] dst) {
        delegate.get(dst);
        return this;
    }

    @Override
    public void transferTo(ByteBuffer buffer) {
        delegate.put(buffer);
    }

    @Override
    public int transferTo(WritableByteChannel sbc) throws IOException {
        return sbc.write(delegate);
    }

    @Override
    public ByteBufferView duplicate() {
        return new DelegatingByteBufferView(delegate.duplicate());
    }


}
