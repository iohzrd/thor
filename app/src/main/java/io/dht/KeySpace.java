package io.dht;

import java.math.BigInteger;

public interface KeySpace {
    // Key converts an identifier into a Key in this space.
    QueryKey Key(byte[] data);

    // Equal returns whether keys are equal in this key space
    boolean Equal(QueryKey a, QueryKey b);

    // Distance returns the distance metric in this key space
    BigInteger Distance(QueryKey a, QueryKey b);

    // Less returns whether the first key is smaller than the second.
    boolean Less(QueryKey a, QueryKey b);
}
