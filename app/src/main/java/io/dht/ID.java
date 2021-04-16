package io.dht;

import androidx.annotation.NonNull;

import com.google.common.primitives.UnsignedBytes;

public class ID implements Comparable<ID> {
    public byte[] data;

    public ID(@NonNull byte[] data) {
        this.data = data;
    }

    @Override
    public int compareTo(@NonNull ID o) {
        //return SignedBytes.lexicographicalComparator().compare(this.data, o.data);
        // TODO check SignedBytes.lexicographicalComparator() or UnsignedBytes.lexicographicalComparator()
        return UnsignedBytes.lexicographicalComparator().compare(this.data, o.data);
    }
}
