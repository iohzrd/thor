package threads.thor.bt.kad.utils;

import java.nio.ByteBuffer;


public final class BitVector {

    private final byte[] vector;
    private final int bits;


    public BitVector(BitVector toCopy) {
        bits = toCopy.bits;
        vector = toCopy.vector.clone();
    }

    public BitVector(int numBits, byte[] rawData) {
        if (numBits > rawData.length * 8)
            throw new IllegalArgumentException("raw data array too small to represent the requested number of bits");
        bits = numBits;
        vector = rawData.clone();
    }


    public BitVector(int numberOfBits) {
        bits = numberOfBits;
        vector = new byte[numberOfBits / 8 + (numberOfBits % 8 != 0 ? 1 : 0)];
    }

    public static int unionAndCount(BitVector... vectors) {
        if (vectors.length == 0)
            return 0;

        int c = 0;
        int bits = vectors[0].size();
        byte union = 0;
        for (int i = 0; i < bits; i++) {
            if (i % 8 == 0) {
                int idx = i / 8;
                union = (byte) 0x00;
                for (BitVector v : vectors)
                    union |= v.vector[idx];
            }
            if ((union & (0x01 << i % 8)) != 0)
                c++;
        }

        return c;
    }

    public void set(int n) {
        vector[n / 8] |= 0x01 << n % 8;
    }

    public boolean get(int n) {
        return (vector[n / 8] & 0x01 << n % 8) != 0;
    }

    private int size() {
        return bits;
    }

    public int bitcount() {
        int c = 0;
        for (int i = 0; i < bits; i++) {
            if ((vector[i / 8] & (0x01 << i % 8)) != 0)
                c++;
        }

        return c;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder(2 * bits / 8);
        for (int i = 0; i < vector.length; i++) {
            if (i % 4 == 0 && i > 0) {
                b.append(' ');
            }
            int nibble = (vector[i] & 0xF0) >> 4;
            b.append((char) (nibble < 0x0A ? '0' + nibble : 'A' + nibble - 10));
            nibble = vector[i] & 0x0F;
            b.append((char) (nibble < 0x0A ? '0' + nibble : 'A' + nibble - 10));
        }
        return b.toString();
    }

    public byte[] getSerializedFormat() {
        return vector.clone();
    }

    public ByteBuffer toBuffer() {
        return ByteBuffer.wrap(vector).asReadOnlyBuffer();
    }

}
