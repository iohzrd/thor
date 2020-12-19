package threads.thor.bt.data.digest;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import threads.thor.bt.data.DataRange;
import threads.thor.bt.data.range.Range;

public class JavaSecurityDigester {

    private final String algorithm;
    private final int step;

    public JavaSecurityDigester(String algorithm, int step) {
        try {
            // verify that implementation for the algorithm exists
            MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("No such algorithm: " + algorithm, e);
        }
        this.algorithm = algorithm;
        this.step = step;
    }


    public byte[] digest(DataRange data) {
        MessageDigest digest = createDigest();

        data.visitUnits((unit, off, lim) -> {
            long remaining = lim - off;
            if (remaining > Integer.MAX_VALUE) {
                throw new RuntimeException("Too much data -- can't read to buffer");
            }
            byte[] bytes = new byte[step];
            do {
                if (remaining < step) {
                    bytes = new byte[(int) remaining];
                }
                int read = unit.readBlock(bytes, off);
                if (read == -1) {
                    // end of data, terminate
                    return;
                } else if (read < bytes.length) {
                    digest.update(Arrays.copyOfRange(bytes, 0, read));
                    remaining -= read;
                    off += read;
                } else {
                    digest.update(bytes);
                    remaining -= step;
                    off += step;
                }
            } while (remaining > 0);

        });

        return digest.digest();
    }


    public byte[] digestForced(DataRange data) {
        MessageDigest digest = createDigest();

        data.visitUnits((unit, off, lim) -> {
            long remaining = lim - off;
            if (remaining > Integer.MAX_VALUE) {
                throw new RuntimeException("Too much data -- can't read to buffer");
            }
            byte[] bytes = new byte[step];
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            do {
                if (remaining < step) {
                    bytes = new byte[(int) remaining];
                    buffer = ByteBuffer.wrap(bytes);
                }
                buffer.clear();
                unit.readBlockFully(buffer, off);
                if (buffer.hasRemaining()) {
                    throw new IllegalStateException("Failed to read data fully: " + buffer.remaining() + " bytes remaining");
                }
                digest.update(bytes);
                remaining -= step;
                off += step;
            } while (remaining > 0);

        });

        return digest.digest();
    }


    public byte[] digest(Range<?> data) {
        MessageDigest digest = createDigest();

        long len = data.length();
        if (len <= step) {
            digest.update(data.getBytes());
        } else {
            for (long i = 0; i < len; i += step) {
                digest.update(data.getSubrange(i, Math.min((len - i), step)).getBytes());
            }
        }
        return digest.digest();
    }

    private MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            // not going to happen
            throw new RuntimeException("Unexpected error", e);
        }
    }
}
