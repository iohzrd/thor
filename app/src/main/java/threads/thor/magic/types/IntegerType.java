package threads.thor.magic.types;

import threads.thor.magic.endian.EndianType;

/**
 * A four-byte integer value which often handles the "long" types when the spec was written.
 *
 * @author graywatson
 */
public class IntegerType extends BaseLongType {

    private static final int BYTES_PER_INTEGER = 4;

    public IntegerType(EndianType endianType) {
        super(endianType);
    }

    @Override
    protected int getBytesPerType() {
        return BYTES_PER_INTEGER;
    }

    @Override
    public long maskValue(long value) {
        return value & 0xFFFFFFFFL;
    }

    @Override
    public int compare(boolean unsignedType, Number extractedValue, Number testValue) {
        if (unsignedType) {
            return LongType.staticCompare(extractedValue, testValue);
        }
        int extractedInt = extractedValue.intValue();
        int testInt = testValue.intValue();
        return Integer.compare(extractedInt, testInt);
    }
}
