package io.dht;

import androidx.annotation.NonNull;

import java.math.BigInteger;

public class QueryKey {

    private final ID key;

    private QueryKey(@NonNull ID key) {
        this.key = key;
    }

    @NonNull
    public static QueryKey createQueryKey(byte[] id) {
        return new QueryKey(Util.ConvertKey(id));
    }

    @NonNull
    public BigInteger Distance(@NonNull QueryKey key) {
        return Util.Distance(this.key, key.key);
    }
}
