package threads.lite.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TimeoutCloseable implements Closeable {

    private final long timeout;
    @Nullable
    private final Closeable closeable;
    private final long start;

    public TimeoutCloseable(long timeout) {
        this.closeable = null;
        this.timeout = timeout;
        this.start = System.currentTimeMillis();
    }

    public TimeoutCloseable(@NonNull Closeable closeable, long timeout) {
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
