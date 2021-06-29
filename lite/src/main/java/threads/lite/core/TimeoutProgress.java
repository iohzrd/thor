package threads.lite.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class TimeoutProgress implements Progress {

    private final long timeout;
    @Nullable
    private final Closeable closeable;
    private final long start;

    public TimeoutProgress(long timeout) {
        this.closeable = null;
        this.timeout = timeout;
        this.start = System.currentTimeMillis();
    }

    public TimeoutProgress(@NonNull Closeable closeable, long timeout) {
        this.closeable = closeable;
        this.timeout = timeout;
        this.start = System.currentTimeMillis();
    }

    @Override
    public boolean isClosed() {
        if (closeable != null) {
            return closeable.isClosed() || (System.currentTimeMillis() - start) > (timeout * 1000);
        }
        return (System.currentTimeMillis() - start) > (timeout * 1000);
    }


}
