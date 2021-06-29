package threads.lite.format;

import androidx.annotation.NonNull;

import threads.lite.cid.Cid;

public interface Block {
    byte[] getRawData();

    Cid getCid();

    @NonNull
    String toString();
}
