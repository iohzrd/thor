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
    public void position(int newPosition) {
        delegate.position(newPosition);
    }

    @Override
    public int limit() {
        return delegate.limit();
    }

    @Override
    public void limit(int newLimit) {
        delegate.limit(newLimit);
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
    public void get(byte[] dst) {
        delegate.get(dst);
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
