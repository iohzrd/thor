package io.core;

import androidx.annotation.NonNull;

import io.ipfs.ipns.Ipns;

public interface Validator {


    @NonNull
    Ipns.Entry validate(@NonNull byte[] key, byte[] value) throws InvalidRecord;

    // return 1 for rec and -1 for cmp and 0 for both equal
    int compare(@NonNull Ipns.Entry rec, @NonNull Ipns.Entry cmp);

}
