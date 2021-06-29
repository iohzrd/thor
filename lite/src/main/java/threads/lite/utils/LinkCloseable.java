package threads.lite.utils;

import androidx.annotation.NonNull;

import threads.lite.core.Closeable;

public interface LinkCloseable extends Closeable {
    void info(@NonNull Link link);
}
