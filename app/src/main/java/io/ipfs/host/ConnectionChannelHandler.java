package io.ipfs.host;

import androidx.annotation.NonNull;

import net.luminis.quic.stream.QuicStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import io.LogUtils;


public abstract class ConnectionChannelHandler {
    private static final String TAG = ConnectionChannelHandler.class.getSimpleName();
    private final Connection connection;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    public ConnectionChannelHandler(@NonNull Connection connection, @NonNull QuicStream quicStream) {
        this.connection = connection;
        this.inputStream = quicStream.getInputStream();
        this.outputStream = quicStream.getOutputStream();
        new Thread(this::reader).start();
    }

    public void reader() {
        ByteBuffer buf = ByteBuffer.allocate(4096);
        try {
            int length;

            while ((length = inputStream.read(buf.array(), 0, buf.capacity())) > 0) {
                byte[] data = Arrays.copyOfRange(buf.array(), 0, length);
                channelRead0(connection, data);
                buf.rewind();
                LogUtils.error(TAG, "Reader active " + new String(data));
            }

        } catch (Throwable throwable) {
            exceptionCaught(connection, throwable);
        } finally {
            LogUtils.error(TAG, "Reader done");
        }
    }

    abstract public void exceptionCaught(@NonNull Connection connection, @NonNull Throwable cause);

    abstract public void channelRead0(@NonNull Connection connection, @NonNull byte[] msg) throws Exception;

    public void writeAndFlush(@NonNull byte[] data) throws IOException {
        outputStream.write(data);
        outputStream.flush();
    }

    public void close() throws IOException {
        closeInputStream();
        closeOutputStream();
    }

    public void closeInputStream() throws IOException {
        inputStream.close();
    }

    public void closeOutputStream() throws IOException {
        outputStream.close();
    }
}
