package threads.thor.bt.net.buffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public interface ByteBufferView {

    int position();

    ByteBufferView position(int newPosition);

    int limit();

    ByteBufferView limit(int newLimit);

    int capacity();

    boolean hasRemaining();

    int remaining();

    byte get();

    short getShort();

    int getInt();

    ByteBufferView get(byte[] dst);

    void transferTo(ByteBuffer buffer);

    int transferTo(WritableByteChannel sbc) throws IOException;

    ByteBufferView duplicate();
}
