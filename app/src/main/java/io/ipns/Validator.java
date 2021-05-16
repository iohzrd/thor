package io.ipns;

import androidx.annotation.NonNull;

public interface Validator {

    // Validate validates the given record, returning an error if it's
    // invalid (e.g., expired, signed by the wrong key, etc.).
    @NonNull
    Ipns.Entry Validate(@NonNull byte[] key, byte[] value) throws InvalidRecord;

    // return 1 for rec and -1 for cmp and 0 for both equal
    int Select(@NonNull Ipns.Entry rec, @NonNull Ipns.Entry cmp);

}
