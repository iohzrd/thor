package threads.thor.magnet.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.time.Duration;
import java.util.Objects;

import threads.thor.magnet.net.buffer.Buffers;

public class ByteChannelReader {

    private final ReadableByteChannel channel;
    private final Duration timeout;
    private final Duration waitBetweenReads;
    private final int min;
    private final int limit;

    private ByteChannelReader(ReadableByteChannel channel,
                              Duration timeout,
                              Duration waitBetweenReads,
                              int min,
                              int limit) {
        if (min < 0 || limit < 0 || limit < min) {
            throw new IllegalArgumentException("Illegal arguments: min (" + min + "), limit (" + limit + ")");
        }
        this.channel = Objects.requireNonNull(channel);
        this.timeout = timeout;
        this.waitBetweenReads = waitBetweenReads;
        this.min = min;
        this.limit = limit;
    }

    public static ByteChannelReader forChannel(ReadableByteChannel channel) {
        return new ByteChannelReader(channel, null, null, 0, Integer.MAX_VALUE);
    }

    public ByteChannelReader withTimeout(Duration timeout) {
        return new ByteChannelReader(channel, timeout, waitBetweenReads, min, limit);
    }

    public ByteChannelReader waitBetweenReads(Duration waitBetweenReads) {
        return new ByteChannelReader(channel, timeout, waitBetweenReads, min, limit);
    }

    public ByteChannelReader readAtLeast(int minBytes) {
        return new ByteChannelReader(channel, timeout, waitBetweenReads, minBytes, limit);
    }

    public ByteChannelReader readNoMoreThan(int maxBytes) {
        return new ByteChannelReader(channel, timeout, waitBetweenReads, min, maxBytes);
    }

    public ByteChannelReader readBetween(int minBytes, int maxBytes) {
        return new ByteChannelReader(channel, timeout, waitBetweenReads, minBytes, maxBytes);
    }

    public int sync(ByteBuffer buf, byte[] syncToken) throws IOException {
        ensureSufficientSpace(buf);
        if (syncToken.length == 0) {
            throw new IllegalArgumentException("Empty synchronization token");
        }

        int searchpos = buf.position(), origlim = buf.limit();
        boolean found = false;
        int matchpos = -1;
        long t1 = System.currentTimeMillis();
        int readTotal = 0;
        int read;
        long timeoutMillis = getTimeoutMillis();
        long waitBetweenReadsMillis = getWaitBetweenReadsMillis();
        do {
            read = channel.read(buf);
            if (read < 0) {
                throw new RuntimeException("Received EOF, total bytes read: " + readTotal + ", expected: " + min + ".." + limit);
            } else if (read > 0) {
                readTotal += read;
                if (readTotal > limit) {
                    throw new IllegalStateException("More than " + limit + " bytes received: " + readTotal);
                }
                if (!found) {
                    int pos = buf.position();
                    buf.flip();
                    buf.position(searchpos);
                    if (buf.remaining() >= syncToken.length) {
                        if (Buffers.searchPattern(buf, syncToken)) {
                            found = true;
                            matchpos = buf.position();
                        } else {
                            searchpos = pos - syncToken.length + 1;
                        }
                    }
                    buf.limit(origlim);
                    buf.position(pos);
                }
            }
            if (found && min > 0 && readTotal >= min) {
                break;
            }
            if (waitBetweenReadsMillis > 0) {
                try {
                    Thread.sleep(waitBetweenReadsMillis);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted while waiting for data", e);
                }
            }
        } while (timeoutMillis == 0 || (System.currentTimeMillis() - t1 <= timeoutMillis));

        if (readTotal < min) {
            throw new IllegalStateException("Less than " + min + " bytes received: " + readTotal);
        } else if (!found) {
            throw new IllegalStateException("Failed to synchronize: expected " + min + ".." + limit + ", received " + readTotal);
        }

        buf.position(matchpos);
        return readTotal;
    }

    public int read(ByteBuffer buf) throws IOException {
        ensureSufficientSpace(buf);

        long t1 = System.currentTimeMillis();
        int readTotal = 0;
        int read;
        long timeoutMillis = getTimeoutMillis();
        long waitBetweenReadsMillis = getWaitBetweenReadsMillis();
        do {
            read = channel.read(buf);
            if (read < 0) {
                throw new RuntimeException("Received EOF, total bytes read: " + readTotal + ", expected: " + min + ".." + limit);
            } else {
                readTotal += read;
            }
            if (readTotal > limit) {
                throw new IllegalStateException("More than " + limit + " bytes received: " + readTotal);
            } else if (min > 0 && readTotal >= min) {
                break;
            }
            if (waitBetweenReadsMillis > 0) {
                try {
                    Thread.sleep(waitBetweenReadsMillis);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted while waiting for data", e);
                }
            }
        } while ((min > 0 && timeoutMillis == 0) || (System.currentTimeMillis() - t1 <= timeoutMillis));

        if (readTotal < min) {
            throw new IllegalStateException("Less than " + min + " bytes received: " + readTotal);
        }

        return readTotal;
    }

    private long getTimeoutMillis() {
        if (timeout != null) {
            return timeout.toMillis();
        }
        return 0L;
    }

    private long getWaitBetweenReadsMillis() {
        if (waitBetweenReads != null) {
            return waitBetweenReads.toMillis();
        }
        return 0L;
    }

    private void ensureSufficientSpace(ByteBuffer buf) {
        if (buf.remaining() < min) {
            throw new IllegalArgumentException("Insufficient space in buffer: " + buf.remaining() + ", required at least: " + min);
        }
    }
}
