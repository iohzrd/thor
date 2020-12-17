package threads.thor.bt.net.buffer;

public class BufferedData {

    private final int length;
    private final ByteBufferView buffer;
    private volatile boolean disposed;

    public BufferedData(ByteBufferView buffer) {
        this.buffer = buffer;
        this.length = buffer.remaining();
    }

    public ByteBufferView buffer() {
        return buffer;
    }

    public int length() {
        return length;
    }

    public void dispose() {
        disposed = true;
    }

    public boolean isDisposed() {
        return disposed;
    }
}
