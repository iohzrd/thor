package io.ipfs.host;

import androidx.annotation.NonNull;

import net.luminis.quic.stream.QuicStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;


public abstract class ConnectionChannelHandler {
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
            while (inputStream.available() > 0) {
                int b = inputStream.read();
                if (b == -1) {
                    if (buf.position() > 0) {
                        channelRead0(connection, buf.array());
                        buf.rewind();
                    }
                    break;
                }
                buf.put((byte) b);
                if (buf.remaining() == 0) {
                    channelRead0(connection, buf.array());
                    buf.rewind();
                }
            }

        } catch (Throwable throwable) {
            exceptionCaught(connection, throwable);
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
