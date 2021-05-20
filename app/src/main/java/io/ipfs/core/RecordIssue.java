package io.ipfs.core;


import androidx.annotation.NonNull;

public class RecordIssue extends Exception {
    public RecordIssue(@NonNull String txt) {
        super(txt);
    }
}
