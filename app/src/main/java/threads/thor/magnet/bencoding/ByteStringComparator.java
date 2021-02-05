package threads.thor.magnet.bencoding;

import java.util.Comparator;

/**
 * Sorts byte arrays as if they were raw strings.
 * <p>
 * This means that negative numbers go after positive numbers,
 * because they represent higher order characters (128-255).
 */
class ByteStringComparator implements Comparator<byte[]> {

    private static final ByteStringComparator instance = new ByteStringComparator();

    static ByteStringComparator comparator() {
        return instance;
    }

    @Override
    public int compare(byte[] o1, byte[] o2) {
        for (int i = 0, j = 0; i < o1.length && j < o2.length; i++, j++) {
            int k = (o1[i] & 0xFF) - (o2[j] & 0xFF);
            if (k != 0) {
                return k;
            }
        }
        return o1.length - o2.length;
    }
}
