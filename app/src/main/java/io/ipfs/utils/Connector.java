package io.ipfs.utils;

import androidx.annotation.NonNull;

public interface Connector {
    boolean ShouldConnect(@NonNull String string);
}
