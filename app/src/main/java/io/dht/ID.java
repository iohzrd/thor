package io.dht;

import androidx.annotation.NonNull;

import com.google.common.primitives.UnsignedBytes;

import java.util.Objects;

public class ID implements Comparable<ID> {
    public byte[] data;

    public ID(@NonNull byte[] data) {
        this.data = data;
    }

    @Override
    public int compareTo(ID o) {

        // TODO check SignedBytes.lexicographicalComparator() or UnsignedBytes.lexicographicalComparator()
        return Objects.compare(this.data, o.data, UnsignedBytes.lexicographicalComparator());
    }
}
