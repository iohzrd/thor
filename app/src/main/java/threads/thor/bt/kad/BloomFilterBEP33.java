package threads.thor.bt.kad;

import androidx.annotation.NonNull;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static java.lang.Math.log1p;


public class BloomFilterBEP33 implements Comparable<BloomFilterBEP33>, Cloneable {

    public final static int m = 256 * 8;
    private final static int k = 2;


    private MessageDigest sha1;
    private BitVector filter;

    BloomFilterBEP33() {
        filter = new BitVector(m);

        try {
            sha1 = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    // the logarithm of the base used for various calculations
    private static double logB() {
        return log1p(-1.0 / m);
    }

    public void insert(InetAddress addr) {

        byte[] hash = sha1.digest(addr.getAddress());

        int index1 = (hash[0] & 0xFF) | (hash[1] & 0xFF) << 8;
        int index2 = (hash[2] & 0xFF) | (hash[3] & 0xFF) << 8;

        // truncate index to m (11 bits required)
        index1 %= m;
        index2 %= m;

        // set bits at index1 and index2
        filter.set(index1);
        filter.set(index2);
    }

    @NonNull
    @Override
    protected BloomFilterBEP33 clone() throws CloneNotSupportedException {
        BloomFilterBEP33 newFilter;

        newFilter = (BloomFilterBEP33) super.clone();

        newFilter.filter = new BitVector(filter);
        return newFilter;
    }

    public int compareTo(BloomFilterBEP33 o) {
        return size() - o.size();
    }

    private int size() {
        // number of expected 0 bits = m * (1 − 1/m)^(k*size)

        double c = filter.bitcount();
        double size = log1p(-c / m) / (k * logB());
        return (int) size;
    }

    public ByteBuffer toBuffer() {
        return filter.toBuffer();
    }

}
