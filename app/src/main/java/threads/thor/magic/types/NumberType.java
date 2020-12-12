package threads.thor.magic.types;

import threads.thor.magic.endian.EndianConverter;
import threads.thor.magic.endian.EndianType;
import threads.thor.magic.entries.MagicFormatter;
import threads.thor.magic.entries.MagicMatcher;

/**
 * Base class for our numbers so we can do generic operations on them.
 *
 * @author graywatson
 */
public abstract class NumberType implements MagicMatcher {

    final EndianConverter endianConverter;

    NumberType(EndianType endianType) {
        this.endianConverter = endianType.getConverter();
    }

    /**
     * Decode the test string value.
     */
    public abstract Number decodeValueString(String valueStr) throws NumberFormatException;

    /**
     * Return the number of bytes in this type.
     */
    protected abstract int getBytesPerType();

    /**
     * Return -1 if extractedValue is &lt; testValue, 1 if it is &gt;, 0 if it is equals.
     */
    public abstract int compare(boolean unsignedType, Number extractedValue, Number testValue);

    /**
     * Return the value with the appropriate bytes masked off corresponding to the bytes in the type.
     */
    public abstract long maskValue(long value);

    @Override
    public Object convertTestString(String typeStr, String testStr) {
        return new NumberComparison(this, testStr);
    }

    @Override
    public Object extractValueFromBytes(int offset, byte[] bytes, boolean required) {
        return endianConverter.convertNumber(offset, bytes, getBytesPerType());
    }

    @Override
    public Object isMatch(Object testValue, Long andValue, boolean unsignedType, Object extractedValue,
                          MutableOffset mutableOffset, byte[] bytes) {
        if (((NumberComparison) testValue).isMatch(andValue, unsignedType, (Number) extractedValue)) {
            mutableOffset.offset += getBytesPerType();
            return extractedValue;
        } else {
            return null;
        }
    }

    @Override
    public void renderValue(StringBuilder sb, Object extractedValue, MagicFormatter formatter) {
        formatter.format(sb, extractedValue);
    }
}
