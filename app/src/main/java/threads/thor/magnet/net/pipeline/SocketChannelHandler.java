package threads.thor.magnet.net.pipeline;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import threads.LogUtils;
import threads.thor.magnet.net.DataReceiver;
import threads.thor.magnet.net.buffer.BorrowedBuffer;
import threads.thor.magnet.protocol.Message;

public class SocketChannelHandler {

    private final SocketChannel channel;
    private final BorrowedBuffer<ByteBuffer> inboundBuffer;
    private final BorrowedBuffer<ByteBuffer> outboundBuffer;
    private final ChannelHandlerContext context;
    private final DataReceiver dataReceiver;

    private final Object inboundBufferLock;
    private final Object outboundBufferLock;
    private final AtomicBoolean shutdown;

    public SocketChannelHandler(
            SocketChannel channel,
            BorrowedBuffer<ByteBuffer> inboundBuffer,
            BorrowedBuffer<ByteBuffer> outboundBuffer,
            Function<SocketChannelHandler, ChannelHandlerContext> contextFactory,
            DataReceiver dataReceiver) {

        this.channel = channel;
        this.inboundBuffer = inboundBuffer;
        this.outboundBuffer = outboundBuffer;
        this.context = contextFactory.apply(this);
        this.dataReceiver = dataReceiver;

        this.inboundBufferLock = new Object();
        this.outboundBufferLock = new Object();
        this.shutdown = new AtomicBoolean(false);
    }


    public void send(Message message) {
        if (context.pipeline().notEncoded(message)) {
            flush();
            if (context.pipeline().notEncoded(message)) {
                throw new IllegalStateException("Failed to send message: " + message);
            }
        }
        flush();
    }


    public Message receive() {
        return context.pipeline().decode();
    }


    public boolean read() {
        try {
            return processInboundData();
        } catch (Exception e) {
            shutdown();
            throw new RuntimeException("Unexpected error", e);
        }
    }


    public void register() {
        dataReceiver.registerChannel(channel, context);
    }


    public void unregister() {
        dataReceiver.unregisterChannel(channel);
    }


    public void activate() {
        dataReceiver.activateChannel(channel);
    }


    public void deactivate() {
        dataReceiver.deactivateChannel(channel);
    }

    private boolean processInboundData() throws IOException {
        synchronized (inboundBufferLock) {
            ByteBuffer buffer = inboundBuffer.lockAndGet();
            try {
                do {
                    int readLast;
                    while ((readLast = channel.read(buffer)) > 0)
                        ;
                    boolean insufficientSpace = !buffer.hasRemaining();
                    context.fireDataReceived();
                    if (readLast == -1) {
                        throw new EOFException();
                    } else if (!insufficientSpace) {
                        return true;
                    }
                } while (buffer.hasRemaining());
                return false;
            } finally {
                inboundBuffer.unlock();
            }
        }
    }


    public void flush() {
        synchronized (outboundBufferLock) {
            ByteBuffer buffer = outboundBuffer.lockAndGet();
            if (buffer == null) {
                // buffer has been released
                return;
            }
            buffer.flip();
            try {
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                buffer.compact();
                outboundBuffer.unlock();
            } catch (IOException e) {
                outboundBuffer.unlock(); // can't use finally block due to possibility of double-unlock
                shutdown();
                throw new RuntimeException("Unexpected I/O error", e);
            }
        }
    }


    public void close() {
        synchronized (inboundBufferLock) {
            synchronized (outboundBufferLock) {
                shutdown();
            }
        }
    }

    private void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            try {
                unregister();
            } catch (Exception e) {
                LogUtils.error(LogUtils.TAG, e);
            }
            closeChannel();
            releaseBuffers();
        }
    }

    private void closeChannel() {
        try {
            channel.close();
        } catch (IOException e) {
            LogUtils.error(LogUtils.TAG, e);
        }
    }

    private void releaseBuffers() {
        releaseBuffer(inboundBuffer);
        releaseBuffer(outboundBuffer);
    }

    private void releaseBuffer(BorrowedBuffer<ByteBuffer> buffer) {
        try {
            buffer.release();
        } catch (Exception e) {
            LogUtils.error(LogUtils.TAG, e);
        }
    }


    public boolean isClosed() {
        return shutdown.get();
    }
}
