package io.ipfs.host;

import androidx.annotation.NonNull;

import net.luminis.quic.stream.QuicStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import io.LogUtils;
import io.ipfs.core.ConnectionIssue;


public abstract class ConnectionChannelHandler {
    private static final String TAG = ConnectionChannelHandler.class.getSimpleName();
    private final Connection connection;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final AtomicBoolean close = new AtomicBoolean(false);
    protected final int streamId;

    public ConnectionChannelHandler(@NonNull Connection connection, @NonNull QuicStream quicStream) {
        this.connection = connection;
        this.inputStream = quicStream.getInputStream();
        this.outputStream = quicStream.getOutputStream();
        this.streamId = quicStream.getStreamId();
        new Thread(this::reader).start();
    }

    public void reader() {
        ByteBuffer buf = ByteBuffer.allocate(4096);
        try {
            int length;

            while (!close.get() && (length = inputStream.read(buf.array(), 0, buf.capacity())) > 0) {
                byte[] data = Arrays.copyOfRange(buf.array(), 0, length);
                channelRead0(connection, data);
                buf.rewind();
            }

        } catch (ProtocolException protocolException) {
            closeInputStream();
            connection.disconnect();
            exceptionCaught(connection, new ConnectionIssue());
        } catch (Throwable throwable) {
            exceptionCaught(connection, throwable);
        } finally {
            buf.clear();
        }
    }

    abstract public void exceptionCaught(@NonNull Connection connection, @NonNull Throwable cause);

    abstract public void channelRead0(@NonNull Connection connection, @NonNull byte[] msg) throws Exception;

    public void writeAndFlush(@NonNull byte[] data) throws IOException {
        outputStream.write(data);
        outputStream.flush();
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
