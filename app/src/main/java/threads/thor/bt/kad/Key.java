package threads.thor.bt.kad;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;

import threads.thor.bt.kad.utils.ThreadLocalUtils;

import static threads.thor.bt.utils.Arrays.compareUnsigned;
import static threads.thor.bt.utils.Arrays.mismatch;

/**
 * @author Damokles
 */
public class Key implements Comparable<Key> {

    public static final Key MIN_KEY;
    public static final Key MAX_KEY;
    public static final int SHA1_HASH_LENGTH = 20;
    public static final int KEY_BITS = SHA1_HASH_LENGTH * 8;

    static {
        MIN_KEY = new Key();
        MAX_KEY = new Key();
        Arrays.fill(MAX_KEY.hash, (byte) 0xFF);
    }

    final byte[] hash = new byte[SHA1_HASH_LENGTH];

    /**
     * A Key in the DHT.
     * <p>
     * Key's in the distributed hash table are just SHA-1 hashes.
     * Key provides all necesarry operators to be used as a value.
     */
    Key() {
    }


    public Key(Key k) {
        System.arraycopy(k.hash, 0, hash, 0, SHA1_HASH_LENGTH);
    }


    public Key(String hex) {
        if (hex.length() != 40)
            throw new IllegalArgumentException("Hex String must have 40 bytes");

        for (int i = 0; i < hex.length(); i += 2)
            hash[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));

    }

    public Key(ByteBuffer buf) {
        buf.get(hash);
    }

    /**
     * Creates a Key with this hash
     *
     * @param hash the SHA1 hash, has to be 20 bytes
     */
    public Key(byte[] hash) {
        if (hash.length != SHA1_HASH_LENGTH) {
            throw new IllegalArgumentException(
                    "Invalid Hash must be 20bytes, was: " + hash.length);
        }
        System.arraycopy(hash, 0, this.hash, 0, SHA1_HASH_LENGTH);
    }

    public static Key setBit(int idx) {
        Key k = new Key();
        k.hash[idx / 8] = (byte) (0x80 >>> (idx % 8));
        return k;
    }


    private static Key distance(Key a, Key b) {
        Key x = new Key();
        for (int i = 0; i < a.hash.length; i++) {
            x.hash[i] = (byte) (a.hash[i] ^ b.hash[i]);
        }
        return x;
    }

    /**
     * Creates a random Key
     *
     * @return newly generated random Key
     */
    public static Key createRandomKey() {
        Key x = new Key();
        ThreadLocalUtils.getThreadLocalRandom().nextBytes(x.hash);
        return x;
    }


    /*
     * compares Keys according to their natural distance
     */
    public int compareTo(Key o) {
        return compareUnsigned(hash, o.hash);
    }

    /**
     * Compares the distance of two keys relative to this one using the XOR metric
     *
     * @return -1 if k1 is closer to this key, 0 if k1 and k2 are equidistant, 1 if k2 is closer
     */
    public int threeWayDistance(Key k1, Key k2) {
        byte[] h1 = k1.hash;
        byte[] h2 = k2.hash;

        int mmi = mismatch(h1, h2);

        if (mmi == -1)
            return 0;

        int h = Byte.toUnsignedInt(hash[mmi]);
        int a = Byte.toUnsignedInt(h1[mmi]);
        int b = Byte.toUnsignedInt(h2[mmi]);

        return Integer.compareUnsigned(a ^ h, b ^ h);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Key) {
            // potential alternative would be a descending comparison since prefix bytes might be shared in sorted data structures
            Key otherKey = (Key) o;
            return Arrays.equals(hash, otherKey.hash);
        }
        return false;
    }

    /**
     * @return the hash
     */
    public byte[] getHash() {
        return hash.clone();
    }

    public void toBuffer(ByteBuffer dst) {
        dst.put(hash);
    }

    public int getByte(int offset) {
        return hash[offset];
    }

    public int getInt(int offset) {
        byte[] hash = this.hash;
        return Byte.toUnsignedInt(hash[offset]) << 24 | Byte.toUnsignedInt(hash[offset + 1]) << 16 | Byte.toUnsignedInt(hash[offset + 2]) << 8 | Byte.toUnsignedInt(hash[offset + 3]);
    }

    public Key getDerivedKey(int idx) {
        Key k = new Key(this);
        idx = Integer.reverse(idx);
        byte[] data = k.hash;
        data[0] ^= (idx >>> 24) & 0xFF;
        data[1] ^= (idx >>> 16) & 0xFF;
        data[2] ^= (idx >>> 8) & 0xFF;
        data[3] ^= idx & 0xFF;
        return k;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        byte[] hash = this.hash;
        return (((hash[0] ^ hash[1] ^ hash[2] ^ hash[3] ^ hash[4]) & 0xff) << 24)
                | (((hash[5] ^ hash[6] ^ hash[7] ^ hash[8] ^ hash[9]) & 0xff) << 16)
                | (((hash[10] ^ hash[11] ^ hash[12] ^ hash[13] ^ hash[14]) & 0xff) << 8)
                | ((hash[15] ^ hash[16] ^ hash[17] ^ hash[18] ^ hash[19]) & 0xff);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @NonNull
    @Override
    public String toString() {
        return toString(true);
    }

    public String toString(boolean nicePrint) {
        StringBuilder b = new StringBuilder(nicePrint ? 44 : 40);
        for (int i = 0; i < hash.length; i++) {
            if (nicePrint && i % 4 == 0 && i > 0) {
                b.append(' ');
            }
            int nibble = (hash[i] & 0xF0) >> 4;
            b.append((char) (nibble < 0x0A ? '0' + nibble : 'A' + nibble - 10));
            nibble = hash[i] & 0x0F;
            b.append((char) (nibble < 0x0A ? '0' + nibble : 'A' + nibble - 10));
        }
        return b.toString();
    }

    /**
     * Returns the approximate distance of this key to the other key.
     * <p>
     * Distance is simplified by returning the index of the first different Bit.
     *
     * @param id Key to compare to.
     * @return integer marking the different bits of the keys
     */
    public int findApproxKeyDistance(Key id) {

        // XOR our id and the sender's ID
        Key d = Key.distance(id, this);

        return d.leadingOneBit();
    }

    private int leadingOneBit() {
        for (int i = 0; i < 20; i++) {
            // get the byte
            int b = hash[i] & 0xFF;
            // no bit on in this byte so continue
            if (b == 0) {
                continue;
            }

            return i * 8 + Integer.numberOfLeadingZeros(b) - 24;
        }
        return -1;
    }


    public Key distance(Key x) {

        return distance(this, x);
    }

    public Key add(Key x) {
        int carry = 0;
        Key out = new Key(this);
        for (int i = 19; i >= 0; i--) {
            carry = Byte.toUnsignedInt(out.hash[i]) + Byte.toUnsignedInt(x.hash[i]) + carry;
            out.hash[i] = (byte) (carry & 0xff);
            carry >>>= 8;
        }

        return out;
    }

    /**
     * sorts the closest entries to the head, the furthest to the tail
     */
    public static final class DistanceOrder implements Comparator<Key> {

        final Key target;

        public DistanceOrder(Key target) {
            this.target = target;
        }


        public int compare(Key o1, Key o2) {
            return target.threeWayDistance(o1, o2);
            //return target.distance(o1).compareTo(target.distance(o2));
        }
    }
}
