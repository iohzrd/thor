package io.core;

import androidx.annotation.NonNull;

public interface Validator {

    // Validate validates the given record, returning an error if it's
    // invalid (e.g., expired, signed by the wrong key, etc.).
    void Validate(@NonNull byte[] key, byte[] value) throws InvalidRecord;

    // return 1 for rec and -1 for cmp and 0 for both equal
    int Select(@NonNull byte[] rec, byte[] cmp);

}
