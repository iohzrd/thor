package io.dht;

import androidx.annotation.NonNull;

public class ID implements Comparable<ID> {
    public byte[] data;

    public ID(@NonNull byte[] data) {
        this.data = data;
    }

    @Override
    public int compareTo(ID o) {

        /* TODO
        QueryKey a = ks.Key {
            Space:
            ks.XORKeySpace, Bytes:id
        }
        QueryKey b = ks.Key {
            Space:
            ks.XORKeySpace, Bytes:other
        }
        return a.Less(b);

         */
        return 0;
    }
}
