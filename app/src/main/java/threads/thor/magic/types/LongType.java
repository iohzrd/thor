package threads.thor.magic.types;

import threads.thor.magic.endian.EndianType;

/**
 * An eight-byte value constituted "quad" when the magic file spec was written.
 *
 * @author graywatson
 */
public class LongType extends BaseLongType {

    private static final int BYTES_PER_LONG = 8;

    public LongType(EndianType endianType) {
        super(endianType);
    }

    /**
     * Static compare of longs which are unsigned or signed.
     */
    public static int staticCompare(Number extractedValue, Number testValue) {
        long extractedLong = extractedValue.longValue();
        long testLong = testValue.longValue();
        return Long.compare(extractedLong, testLong);
    }

    /**
     * Return the number of bytes in this type.
     */
    @Override
    public int getBytesPerType() {
        return BYTES_PER_LONG;
    }

    @Override
    public long maskValue(long value) {
        return value;
    }

    @Override
    public int compare(boolean unsignedType, Number extractedValue, Number testValue) {
        return staticCompare(extractedValue, testValue);
    }
}
