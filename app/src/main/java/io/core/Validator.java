package io.core;

import androidx.annotation.NonNull;

import java.util.List;

public interface Validator {

    // Validate validates the given record, returning an error if it's
    // invalid (e.g., expired, signed by the wrong key, etc.).
    void Validate(@NonNull String key, byte[] value) throws InvalidRecord;

    // Select selects the best record from the set of records (e.g., the
    // newest).
    //
    // Decisions made by select should be stable.
    int Select(String key, List<byte[]> values);

}
