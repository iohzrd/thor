package threads.lite.ipns;

import androidx.annotation.NonNull;

import threads.lite.core.RecordIssue;

public interface Validator {


    @NonNull
    Ipns.Entry validate(@NonNull byte[] key, byte[] value) throws RecordIssue;

    // return 1 for rec and -1 for cmp and 0 for both equal
    int compare(@NonNull Ipns.Entry rec, @NonNull Ipns.Entry cmp);

}
