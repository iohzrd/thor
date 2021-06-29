package threads.lite.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


public interface Storage {
    void insertBlock(@NonNull String id, @NonNull byte[] bytes);

    @Nullable
    byte[] getData(@NonNull String id);

    void deleteBlock(@NonNull String id);

    int sizeBlock(@NonNull String id);

    boolean hasBlock(@NonNull String id);
}
