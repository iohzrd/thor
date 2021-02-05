package threads.thor.magnet.net.buffer;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class Buffers {

    /**
     * Searches for the first pattern match in the provided buffer.
     * Buffer's position might change in the following ways after this methods returns:
     * - if a pattern was not found, then the buffer's position will not change
     * - if a pattern was found, then the buffer's position will be right after the last index
     * of the matching subrange (probably equal to buffer's limit)
     *
     * @return true if pattern was found in the provided buffer
     * @since 1.2
     */
    public static boolean searchPattern(ByteBuffer buf, byte[] pattern) {
        if (pattern.length == 0) {
            throw new IllegalArgumentException("Empty pattern");
        } else if (buf.remaining() < pattern.length) {
            return false;
        }

        int pos = buf.position();

        int len = pattern.length;
        int p = 31;
        int mul_t0 = 1;
        for (int i = 0; i < len - 1; i++) {
            mul_t0 *= p;
        }
        int hash = 0;
        for (int i = 0; i < len - 1; i++) {
            hash += pattern[i];
            hash *= p;
        }
        hash += pattern[len - 1];

        int buf_hash = 0;
        byte[] bytes = new byte[len];
        byte b;
        for (int i = 0; i < len - 1; i++) {
            b = buf.get();
            bytes[i] = b;
            buf_hash += b;
            buf_hash *= p;
        }
        b = buf.get();
        bytes[bytes.length - 1] = b;
        buf_hash += b;

        boolean found = false;
        do {
            if (buf_hash == hash && Arrays.equals(pattern, bytes)) {
                found = true;
                break;
            } else if (!buf.hasRemaining()) {
                break;
            }
            byte next = buf.get();
            buf_hash -= (bytes[0] * mul_t0);
            buf_hash *= p;
            buf_hash += next;
            System.arraycopy(bytes, 1, bytes, 0, len - 1);
            bytes[len - 1] = next;
        } while (true);

        if (!found) {
            buf.position(pos);
        }
        return found;
    }
}
