package threads.thor.bt.net.buffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.WritableByteChannel;

import threads.thor.bt.protocol.Protocols;

public class SplicedByteBufferView implements ByteBufferView {

    private final ByteBuffer left;
    private final ByteBuffer right;

    private final int leftOffset;
    private final int leftLimit;
    private final int leftCapacity;
    private final int rightOffset;
    private final int rightLimit;
    private final byte[] shortBytes;
    private final byte[] intBytes;
    private final int capacity;
    private int position;
    private int limit;

    public SplicedByteBufferView(ByteBuffer left, ByteBuffer right) {
        this(left, right, 0,
                left.remaining() + right.remaining(),
                left.remaining() + right.remaining(),
                left.position(), left.limit(),
                right.position(), right.limit());
        // mandate that the order is BE, otherwise it will be impossible to read multi-byte numbers,
        // that sit on the boundary of two buffers
        if (left.order() != ByteOrder.BIG_ENDIAN || right.order() != ByteOrder.BIG_ENDIAN) {
            throw new IllegalArgumentException("Byte order must be big-endian for both buffers");
        }
    }

    private SplicedByteBufferView(
            ByteBuffer left, ByteBuffer right,
            int position, int limit, int capacity,
            int leftOffset, int leftLimit, int rightOffset, int rightLimit) {
        this.left = left;
        this.right = right;
        this.position = position;
        this.leftOffset = leftOffset;
        this.leftLimit = leftLimit;
        this.leftCapacity = leftLimit - leftOffset;
        this.rightOffset = rightOffset;
        this.rightLimit = rightLimit;
        this.limit = limit;
        this.capacity = capacity;
        this.shortBytes = new byte[Short.BYTES];
        this.intBytes = new byte[Integer.BYTES];
    }

    @Override
    public int position() {
        return position;
    }

    @Override
    public ByteBufferView position(int newPosition) {
        if (newPosition > limit) {
            throw new IllegalArgumentException("Position is greater than limit: " + newPosition + " > " + limit);
        } else if (newPosition < 0) {
            throw new IllegalArgumentException("Negative position: " + newPosition);
        }
        position = newPosition;
        if (position >= leftCapacity) {
            left.position(leftLimit);
            right.position(rightOffset + (position - leftCapacity));
        } else {
            left.position(leftOffset + position);
            right.position(rightOffset);
        }
        return this;
    }

    @Override
    public int limit() {
        return limit;
    }

    @Override
    public ByteBufferView limit(int newLimit) {
        if (newLimit > capacity) {
            throw new IllegalArgumentException("Limit is greater than capacity: " + newLimit + " > " + capacity);
        } else if (newLimit < 0) {
            throw new IllegalArgumentException("Negative limit: " + newLimit);
        }
        limit = newLimit;
        if (limit >= leftCapacity) {
            left.limit(leftLimit);
            right.limit(limit - leftCapacity);
        } else {
            left.limit(leftOffset + limit);
            right.limit(rightOffset);
        }
        if (position > limit) {
            position = limit;
        }
        return this;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public boolean hasRemaining() {
        return position < limit;
    }

    @Override
    public int remaining() {
        return limit - position;
    }

    @Override
    public byte get() {
        if (position >= limit) {
            throw new IllegalArgumentException("Insufficient space: " + position + " >= " + limit);
        }
        if (position++ >= leftCapacity) {
            return right.get();
        } else {
            return left.get();
        }
    }

    @Override
    public short getShort() {
        readBytes(shortBytes);
        return Protocols.readShort(shortBytes, 0);
    }

    @Override
    public int getInt() {
        readBytes(intBytes);
        return Protocols.readInt(intBytes, 0);
    }

    @Override
    public ByteBufferView get(byte[] dst) {
        readBytes(dst);
        return this;
    }

    private void readBytes(byte[] dst) {
        if (limit - position < dst.length) {
            throw new IllegalArgumentException("Insufficient space: " + (limit - position) + " < " + dst.length);
        }
        if (left.remaining() >= dst.length) {
            left.get(dst);
        } else if (!left.hasRemaining()) {
            right.get(dst);
        } else {
            // combine from two buffers
            int bytesFromLeft = left.remaining();
            int bytesFromRight = dst.length - left.remaining();
            left.get(dst, 0, left.remaining());
            right.get(dst, bytesFromLeft, bytesFromRight);
        }
        position += dst.length;
    }

    @Override
    public void transferTo(ByteBuffer buffer) {
        if (buffer.hasRemaining() && position < leftCapacity) {
            int count = Math.min(buffer.remaining(), left.remaining());
            buffer.put(left);
            position += count;
        }
        if (buffer.hasRemaining() && limit > leftCapacity) {
            int count = Math.min(buffer.remaining(), right.remaining());
            buffer.put(right);
            position += count;
        }
    }

    @Override
    public int transferTo(WritableByteChannel sbc) throws IOException {
        if (position < leftCapacity) {
            int written = sbc.write(left);
            position += written;
            return written;
        } else if (limit > leftCapacity) {
            int written = sbc.write(right);
            position += written;
            return written;
        }
        return 0;
    }

    @Override
    public ByteBufferView duplicate() {
        ByteBuffer leftDup = left.duplicate();
        ByteBuffer rightDup = right.duplicate();
        return new SplicedByteBufferView(leftDup, rightDup,
                position, limit, capacity, leftOffset, leftLimit, rightOffset, rightLimit);
    }

}
