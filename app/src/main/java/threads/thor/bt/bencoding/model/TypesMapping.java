package threads.thor.bt.bencoding.model;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import threads.thor.bt.bencoding.BEType;

class TypesMapping {

    public static Class<?> getJavaTypeForBEType(BEType type) {

        switch (type) {
            case MAP:
                return Map.class;
            case LIST:
                return List.class;
            case INTEGER:
                return BigInteger.class;
            case STRING:
                return byte[].class;
            default: {
                throw new IllegalArgumentException("Unknown BE type: " + type);
            }
        }
    }
}
