package threads.thor.magnet.net.buffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public interface ByteBufferView {

    int position();

    void position(int newPosition);

    int limit();

    void limit(int newLimit);

    int capacity();

    boolean hasRemaining();

    int remaining();

    byte get();

    short getShort();

    int getInt();

    void get(byte[] dst);

    void transferTo(ByteBuffer buffer);

    int transferTo(WritableByteChannel sbc) throws IOException;

    ByteBufferView duplicate();
}
