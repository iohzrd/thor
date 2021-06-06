package io.ipfs.unixfs;

import android.util.Pair;

/**
 * Abstracts bit field operations.
 */
public class BitField {

    static final int[] pop8tab = {
            0x00, 0x01, 0x01, 0x02, 0x01, 0x02, 0x02, 0x03, 0x01, 0x02, 0x02, 0x03, 0x02, 0x03, 0x03, 0x04,
            0x01, 0x02, 0x02, 0x03, 0x02, 0x03, 0x03, 0x04, 0x02, 0x03, 0x03, 0x04, 0x03, 0x04, 0x04, 0x05,
            0x01, 0x02, 0x02, 0x03, 0x02, 0x03, 0x03, 0x04, 0x02, 0x03, 0x03, 0x04, 0x03, 0x04, 0x04, 0x05,
            0x02, 0x03, 0x03, 0x04, 0x03, 0x04, 0x04, 0x05, 0x03, 0x04, 0x04, 0x05, 0x04, 0x05, 0x05, 0x06,
            0x01, 0x02, 0x02, 0x03, 0x02, 0x03, 0x03, 0x04, 0x02, 0x03, 0x03, 0x04, 0x03, 0x04, 0x04, 0x05,
            0x02, 0x03, 0x03, 0x04, 0x03, 0x04, 0x04, 0x05, 0x03, 0x04, 0x04, 0x05, 0x04, 0x05, 0x05, 0x06,
            0x02, 0x03, 0x03, 0x04, 0x03, 0x04, 0x04, 0x05, 0x03, 0x04, 0x04, 0x05, 0x04, 0x05, 0x05, 0x06,
            0x03, 0x04, 0x04, 0x05, 0x04, 0x05, 0x05, 0x06, 0x04, 0x05, 0x05, 0x06, 0x05, 0x06, 0x06, 0x07,
            0x01, 0x02, 0x02, 0x03, 0x02, 0x03, 0x03, 0x04, 0x02, 0x03, 0x03, 0x04, 0x03, 0x04, 0x04, 0x05,
            0x02, 0x03, 0x03, 0x04, 0x03, 0x04, 0x04, 0x05, 0x03, 0x04, 0x04, 0x05, 0x04, 0x05, 0x05, 0x06,
            0x02, 0x03, 0x03, 0x04, 0x03, 0x04, 0x04, 0x05, 0x03, 0x04, 0x04, 0x05, 0x04, 0x05, 0x05, 0x06,
            0x03, 0x04, 0x04, 0x05, 0x04, 0x05, 0x05, 0x06, 0x04, 0x05, 0x05, 0x06, 0x05, 0x06, 0x06, 0x07,
            0x02, 0x03, 0x03, 0x04, 0x03, 0x04, 0x04, 0x05, 0x03, 0x04, 0x04, 0x05, 0x04, 0x05, 0x05, 0x06,
            0x03, 0x04, 0x04, 0x05, 0x04, 0x05, 0x05, 0x06, 0x04, 0x05, 0x05, 0x06, 0x05, 0x06, 0x06, 0x07,
            0x03, 0x04, 0x04, 0x05, 0x04, 0x05, 0x05, 0x06, 0x04, 0x05, 0x05, 0x06, 0x05, 0x06, 0x06, 0x07,
            0x04, 0x05, 0x05, 0x06, 0x05, 0x06, 0x06, 0x07, 0x05, 0x06, 0x06, 0x07, 0x06, 0x07, 0x07, 0x08,
    };
    private final byte[] bytes;

    private BitField(int val) {
        bytes = new byte[val];
    }

    // NewBitfield creates a new fixed-sized Bitfield (allocated up-front).
//
// Panics if size is not a multiple of 8.
    public static BitField NewBitfield(int size) {
        if (size % 8 != 0) {
            throw new RuntimeException("Bitfield size must be a multiple of 8");
        }
        return new BitField(size / 8);
    }

    // FromBytes constructs a new bitfield from a serialized bitfield.
    public static BitField FromBytes(int size, byte[] bits) {
        BitField bf = NewBitfield(size);
        int start = bf.bytes.length - bits.length;
        if (start < 0) {
            throw new RuntimeException("bitfield too small");
        }

        System.arraycopy(bits, 0, bf.bytes, start, bits.length);
        return bf;
    }

    Pair<Integer, Integer> offset(int i) /*(uint, uint8)*/ {
        return Pair.create((bytes.length) - (((byte) (i) & 0xFF) / 8) - 1, ((byte) (i) & 0xFF) % 8);
    }

    /*

    // Bytes returns the Bitfield as a byte string.
    //
    // This function *does not* copy.
        func (bf Bitfield) Bytes() []byte {
            for i, b := range bf {
                if b != 0 {
                    return bf[i:]
                }
            }
            return nil
        }
    */
    // Bit returns the ith bit.
//
// Panics if the bit is out of bounds.
    public boolean Bit(int i) {
        Pair<Integer, Integer> res = offset(i);
        int idx = res.first;
        int off = res.second;
        int tt = bytes[idx] >> off;
        return (tt & 0x1) != 0;
    }

    /*
        // SetBit sets the ith bit.
    //
    // Panics if the bit is out of bounds.
        func (bf Bitfield) SetBit(i int) {
            idx, off := bf.offset(i)
            bf[idx] |= 1 << off
        }

        // UnsetBit unsets the ith bit.
    //
    // Panics if the bit is out of bounds.
        func (bf Bitfield) UnsetBit(i int) {
            idx, off := bf.offset(i)
            bf[idx] &= 0xFF ^ (1 << off)
        }
    */
    // SetBytes sets the bits to the given byte array.
//
// Panics if 'b' is larger than the bitfield.
    public void SetBytes(byte[] b) {

        int start = bytes.length - b.length;
        if (start < 0) {
            throw new RuntimeException("bitfield too small");
        }
        for (int i = 0; i < start; i++) {
            bytes[i] = 0;
        }
        System.arraycopy(b, 0, bytes, start, b.length);
    }

    /*
        // Ones returns the number of bits set.
        func (bf Bitfield) Ones() int {
            cnt := 0
            for _, b := range bf {
                cnt += bits.OnesCount8(b)
            }
            return cnt
        }
    */
    // OnesBefore returns the number of bits set *before* this bit.
    int OnesBefore(int i) {
        Pair<Integer, Integer> res = offset(i);
        int idx = res.first;
        int off = res.second;

        int cnt = pop8tab[bytes[idx] << (8 - off)];

        for (int j = idx + 1; j < bytes.length; j++) {
            cnt += pop8tab[bytes[j]];
        }
        return cnt;
    }

    /*
     */
    // OnesAfter returns the number of bits set *after* this bit.
    int OnesAfter(int i) {
        /*
        idx, off := bf.offset(i)
        cnt := bits.OnesCount8(bf[idx] >> off)
        for _, b := range bf[:idx] {
            cnt += bits.OnesCount8(b)
        }
        return cnt
         */
        return 0;
    }
    public static int logtwo(int v) {
        if(v <= 0) {
            throw new RuntimeException("hamt size should be a power of two");
        }
        int  lg2 = Integer.numberOfTrailingZeros(v);
        if( 1<<lg2 != v) {
            throw new RuntimeException("hamt size should be a power of two");
        }
        return lg2;
    }


    // Bytes returns the Bitfield as a byte string.
//
// This function *does not* copy.
    public byte[] Bytes() {

        return bytes;
        /* TODO
        for i, b := range bf {
            if b != 0 {
                return bf[i:]
            }
        }
        return nil */
    }

/*
    public static int logtwo(int v) {
        if(v <= 0) {
            throw new RuntimeException("hamt size should be a power of two");
        }
        int lg2 = TrailingZeros(v);
        if( 1 << lg2 != v ) {
            throw new RuntimeException("hamt size should be a power of two");
        }
        return lg2;
    }

private static int UintSize = 32 << (~(byte)(0) >> 32 & 1); // 32 or 64

    // TrailingZeros returns the number of trailing zero bits in x; the result is UintSize for x == 0.
    static int TrailingZeros(int x) {
        if(UintSize == 32) {
            return TrailingZeros32((int)x);
        }
        return TrailingZeros64((long)x);
    }

    // TrailingZeros32 returns the number of trailing zero bits in x; the result is 32 for x == 0.
    func TrailingZeros32(x uint32) int {
        if x == 0 {
            return 32
        }
        // see comment in TrailingZeros64
        return int(deBruijn32tab[(x&-x)*deBruijn32>>(32-5)])
    }

    // TrailingZeros64 returns the number of trailing zero bits in x; the result is 64 for x == 0.
    func TrailingZeros64(x uint64) int {
        if x == 0 {
            return 64
        }
        // If popcount is fast, replace code below with return popcount(^x & (x - 1)).
        //
        // x & -x leaves only the right-most bit set in the word. Let k be the
        // index of that bit. Since only a single bit is set, the value is two
        // to the power of k. Multiplying by a power of two is equivalent to
        // left shifting, in this case by k bits. The de Bruijn (64 bit) constant
        // is such that all six bit, consecutive substrings are distinct.
        // Therefore, if we have a left shifted version of this constant we can
        // find by how many bits it was shifted by looking at which six bit
        // substring ended up at the top of the word.
        // (Knuth, volume 4, section 7.3.1)
        return int(deBruijn64tab[(x&-x)*deBruijn64>>(64-6)])
    }*/
}

