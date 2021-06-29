package threads.lite.host;

import androidx.annotation.NonNull;

import net.luminis.quic.stream.QuicStream;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import threads.lite.LogUtils;


public abstract class QuicStreamHandler {
    private static final String TAG = QuicStreamHandler.class.getSimpleName();
    protected final int streamId;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final AtomicBoolean close = new AtomicBoolean(false);

    public QuicStreamHandler(@NonNull QuicStream quicStream) {
        this.inputStream = quicStream.getInputStream();
        this.outputStream = quicStream.getOutputStream();
        this.streamId = quicStream.getStreamId();
    }

    protected void reading() {
        ByteBuffer buf = ByteBuffer.allocate(4096);
        try {
            int length;

            while (!close.get() && (length = inputStream.read(buf.array(), 0, buf.capacity())) > 0) {
                byte[] data = Arrays.copyOfRange(buf.array(), 0, length);
                channelRead0(data);
                buf.rewind();
            }

        } catch (Throwable throwable) {
            closeInputStream();
            closeOutputStream();
            exceptionCaught(throwable);
        } finally {
            buf.clear();
        }
    }

    abstract public void exceptionCaught(@NonNull Throwable cause);

    abstract public void channelRead0(@NonNull byte[] msg) throws Exception;

    public void writeAndFlush(@NonNull byte[] data) {
        try {
            outputStream.write(data);
            outputStream.flush();
        } catch (Throwable throwable) {
            closeOutputStream();
            exceptionCaught(throwable);
        }
    }


    public void closeInputStream() {
        try {
            inputStream.close();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        } finally {
            close.set(true);
        }
    }

    public void closeOutputStream() {
        try {
            outputStream.close();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }
}
