package threads.thor.magic.types;

import threads.thor.magic.endian.EndianType;

/**
 * Base class for those types which use long types to compare.
 *
 * @author graywatson
 */
public abstract class BaseLongType extends NumberType {

    BaseLongType(EndianType endianType) {
        super(endianType);
    }

    @Override
    public Number decodeValueString(String valueStr) throws NumberFormatException {
        return Long.decode(valueStr);
    }

    @Override
    public byte[] getStartingBytes(Object testValue) {
        return endianConverter.convertToByteArray(((NumberComparison) testValue).getValue().longValue(),
                getBytesPerType());
    }
}
