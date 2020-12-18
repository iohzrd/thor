package threads.thor.bt.utils;


public class Arrays {


    public static int compareUnsigned(byte[] a, byte[] b) {
        try {
            return compareUnsignedFallback(a, b); // TODO compareUnsigned in java.util.Arrays (java 9)
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new Error("should not happen", e);
        }

    }

    public static int mismatch(byte[] a, byte[] b) {
        try {
            return mismatchFallback(a, b); // TODO mismatch in java.util.Arrays (java 9)
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new Error("should not happen", e);
        }

    }


    private static int compareUnsignedFallback(byte[] a, byte[] b) {
        int minLength = Math.min(a.length, b.length);
        for (int i = 0; i + 7 < minLength; i += 8) {
            long la = Byte.toUnsignedLong(a[i]) << 56 |
                    Byte.toUnsignedLong(a[i + 1]) << 48 |
                    Byte.toUnsignedLong(a[i + 2]) << 40 |
                    Byte.toUnsignedLong(a[i + 3]) << 32 |
                    Byte.toUnsignedLong(a[i + 4]) << 24 |
                    Byte.toUnsignedLong(a[i + 5]) << 16 |
                    Byte.toUnsignedLong(a[i + 6]) << 8 |
                    Byte.toUnsignedLong(a[i + 7]);
            long lb = Byte.toUnsignedLong(b[i]) << 56 |
                    Byte.toUnsignedLong(b[i + 1]) << 48 |
                    Byte.toUnsignedLong(b[i + 2]) << 40 |
                    Byte.toUnsignedLong(b[i + 3]) << 32 |
                    Byte.toUnsignedLong(b[i + 4]) << 24 |
                    Byte.toUnsignedLong(b[i + 5]) << 16 |
                    Byte.toUnsignedLong(b[i + 6]) << 8 |
                    Byte.toUnsignedLong(b[i + 7]);

            if (la != lb)
                return Long.compareUnsigned(la, lb);

        }


        for (int i = 0; i < minLength; i++) {
            int ia = Byte.toUnsignedInt(a[i]);
            int ib = Byte.toUnsignedInt(b[i]);
            if (ia != ib)
                return Integer.compare(ia, ib);
        }

        return a.length - b.length;
    }


    private static int mismatchFallback(byte[] a, byte[] b) {
        int min = Math.min(a.length, b.length);
        for (int i = 0; i < min; i++) {
            if (a[i] != b[i])
                return i;
        }

        return a.length == b.length ? -1 : min;
    }

}
