package threads.thor.bt.protocol;

import java.nio.Buffer;
import java.util.Arrays;
import java.util.BitSet;

import threads.thor.bt.BtException;
import threads.thor.bt.net.buffer.ByteBufferView;

public class Protocols {

    //-------------------------//
    //--- utility functions ---//
    //-------------------------//

    /**
     * Get 2-bytes binary representation of a {@link Short}.
     *
     * @since 1.0
     */
    public static byte[] getShortBytes(int s) {
        return new byte[]{
                (byte) (s >> 8),
                (byte) s};
    }

    /**
     * Decode the binary representation of an {@link Integer} from a byte array.
     *
     * @param bytes  Arbitrary byte array.
     *               It's length must be at least <b>offset</b> + 4.
     * @param offset Offset in byte array to start decoding from (inclusive, 0-based)
     * @since 1.0
     */
    public static int readInt(byte[] bytes, int offset) {

        if (bytes.length < offset + Integer.BYTES) {
            throw new ArrayIndexOutOfBoundsException("insufficient byte array length (length: " + bytes.length +
                    ", offset: " + offset + ")");
        }
        return ((bytes[offset] & 0xFF) << 24) |
                ((bytes[offset + 1] & 0xFF) << 16) |
                ((bytes[offset + 2] & 0xFF) << 8) |
                (bytes[offset + 3] & 0xFF);
    }

    /**
     * Decode the binary representation of an {@link Integer} from a buffer.
     *
     * @param buffer Buffer to read from.
     *               Decoding will be done starting with the index denoted by {@link Buffer#position()}
     * @return Decoded value, or null if there are insufficient bytes in buffer
     * (i.e. <b>buffer.remaining()</b> &lt; 4)
     * @since 1.9
     */
    public static Integer readInt(ByteBufferView buffer) {
        if (buffer.remaining() < Integer.BYTES) {
            return null;
        }
        return buffer.getInt();
    }

    /**
     * Decode the binary representation of a {@link Short} from a byte array.
     *
     * @param bytes  Arbitrary byte array.
     *               It's length must be at least <b>offset</b> + 2.
     * @param offset Offset in byte array to start decoding from (inclusive, 0-based)
     * @since 1.0
     */
    public static short readShort(byte[] bytes, int offset) {

        if (bytes.length < offset + Short.BYTES) {
            throw new ArrayIndexOutOfBoundsException("insufficient byte array length (length: " + bytes.length +
                    ", offset: " + offset + ")");
        }
        return (short) (((bytes[offset] & 0xFF) << 8) |
                ((bytes[offset + 1] & 0xFF)));
    }

    /**
     * Decode the binary representation of a {@link Short} from a buffer.
     *
     * @param buffer Buffer to read from.
     *               Decoding will be done starting with the index denoted by {@link Buffer#position()}
     * @return Decoded value, or null if there are insufficient bytes in buffer
     * (i.e. <b>buffer.remaining()</b> &lt; 2)
     * @since 1.9
     */
    public static Short readShort(ByteBufferView buffer) {
        if (buffer.remaining() < Short.BYTES) {
            return null;
        }
        return buffer.getShort();
    }

    /**
     * Convenience method to check if actual message length is the same as expected length.
     *
     * @throws InvalidMessageException if <b>expectedLength</b> != <b>actualLength</b>
     * @since 1.0
     */
    public static void verifyPayloadHasLength(Class<? extends Message> type, int expectedLength, int actualLength) {
        if (expectedLength != actualLength) {
            throw new InvalidMessageException("Unexpected payload length for " + type.getSimpleName() + ": " + actualLength +
                    " (expected " + expectedLength + ")");
        }
    }

    /**
     * Sets i-th bit in a bitmask.
     *
     * @param bytes    Bitmask.
     * @param bitOrder Order of bits in a byte
     * @param i        Bit index (0-based)
     * @since 1.7
     */
    public static void setBit(byte[] bytes, BitOrder bitOrder, int i) {
        int byteIndex = (int) (i / 8d);
        if (byteIndex >= bytes.length) {
            throw new BtException("bit index is too large: " + i);
        }

        int bitIndex = i % 8;
        int shift = (bitOrder == BitOrder.BIG_ENDIAN) ? bitIndex : (7 - bitIndex);
        int bitMask = 0b1 << shift;
        byte currentByte = bytes[byteIndex];
        bytes[byteIndex] = (byte) (currentByte | bitMask);
    }

    /**
     * Gets i-th bit in a bitmask.
     *
     * @param bytes    Bitmask.
     * @param bitOrder Order of bits in a byte
     * @param i        Bit index (0-based)
     * @return 1 if bit is set, 0 otherwise
     * @since 1.7
     */
    public static int getBit(byte[] bytes, BitOrder bitOrder, int i) {
        int byteIndex = (int) (i / 8d);
        if (byteIndex >= bytes.length) {
            throw new BtException("bit index is too large: " + i);
        }

        int bitIndex = i % 8;
        int shift = (bitOrder == BitOrder.BIG_ENDIAN) ? bitIndex : (7 - bitIndex);
        int bitMask = 0b1 << shift;
        return (bytes[byteIndex] & bitMask) >> shift;
    }

    /**
     * Check if i-th bit in the bitmask is set.
     *
     * @param bytes    Bitmask.
     * @param bitOrder Order of bits in a byte
     * @param i        Bit index (0-based)
     * @return true if i-th bit in the bitmask is set, false otherwise
     * @since 1.7
     */
    public static boolean isSet(byte[] bytes, BitOrder bitOrder, int i) {
        return getBit(bytes, bitOrder, i) == 1;
    }

    /**
     * Get hex-encoded representation of a binary array.
     *
     * @param bytes Binary data
     * @return String containing hex-encoded representation (lower case)
     * @since 1.3
     */
    public static String toHex(byte[] bytes) {
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Empty array");
        }
        char[] chars = new char[bytes.length * 2];
        for (int i = 0, j = 0; i < bytes.length; i++, j = i * 2) {
            int b = bytes[i] & 0xFF;
            chars[j] = forHexDigit(b / 16);
            chars[j + 1] = forHexDigit(b % 16);
        }
        return new String(chars);
    }

    private static char forHexDigit(int b) {
        if (b < 0 || b >= 16) {
            throw new IllegalArgumentException("Illegal hexadecimal digit: " + b);
        }
        return (b < 10) ? (char) ('0' + b) : (char) ('a' + b - 10);
    }

    /**
     * Get binary data from its' hex-encoded representation (regardless of case).
     *
     * @param s Hex-encoded representation of binary data
     * @return Binary data
     * @since 1.3
     */
    public static byte[] fromHex(String s) {
        if (s.isEmpty() || s.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid string: " + s);
        }
        char[] chars = s.toCharArray();
        int len = chars.length / 2;
        byte[] bytes = new byte[len];
        for (int i = 0, j = 0; i < len; i++, j = i * 2) {
            bytes[i] = (byte) (hexDigit(chars[j]) * 16 + hexDigit(chars[j + 1]));
        }
        return bytes;
    }

    /**
     * Get 20-bytes long info hash from its' base32-encoded representation (regardless of case).
     *
     * @param s base32-encoded representation of info hash
     * @return Binary data
     * @throws IllegalArgumentException if {@code s.length()} is not 32 characters long
     * @since 1.8
     */
    public static byte[] infoHashFromBase32(String s) {
        if (s.length() != 32) {
            throw new IllegalArgumentException("Invalid string: " + s);
        }
        String base32CodeBase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder hexCache = new StringBuilder();
        for (int i = 0; i < s.length(); i += 4) {
            int hexValue = base32CodeBase.indexOf(s.charAt(i)) << 15 |
                    base32CodeBase.indexOf(s.charAt(i + 1)) << 10 |
                    base32CodeBase.indexOf(s.charAt(i + 2)) << 5 |
                    base32CodeBase.indexOf(s.charAt(i + 3));
            hexCache.append(String.format("%05X", hexValue));
        }
        return fromHex(hexCache.toString());
    }

    private static int hexDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        } else if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        } else if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        throw new IllegalArgumentException("Illegal hexadecimal character: " + c);
    }

    /**
     * Returns a copy of the provided byte array with each byte reversed.
     *
     * @return A copy of the provided byte array with each byte reversed.
     * @since 1.7
     */
    public static byte[] reverseBits(byte[] bytes) {
        byte[] arr = Arrays.copyOf(bytes, bytes.length);
        for (int i = 0; i < arr.length; i++) {
            arr[i] = reverseBits(arr[i]);
        }
        return arr;
    }

    /**
     * Returns a reversed byte.
     *
     * @return A reversed byte.
     * @since 1.7
     */
    private static byte reverseBits(byte b) {
        int i = b;
        // swap halves
        i = (i & 0b11110000) >> 4 | (i & 0b00001111) << 4;
        // swap adjacent pairs
        i = (i & 0b11001100) >> 2 | (i & 0b00110011) << 2;
        // swap adjacent bits
        i = (i & 0b10101010) >> 1 | (i & 0b01010101) << 1;
        return (byte) i;
    }

    /**
     * Get a copy of the provided bitset.
     *
     * @return A copy of the provided bitset.
     * @since 1.7
     */
    public static BitSet copyOf(BitSet bitSet) {
        return (BitSet) bitSet.clone();
    }
}
