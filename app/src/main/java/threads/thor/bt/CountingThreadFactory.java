package threads.thor.bt;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public class CountingThreadFactory implements ThreadFactory {

    private final String prefix;
    private final boolean daemon;
    private final AtomicLong threadIndex;

    private CountingThreadFactory(String prefix, boolean daemon) {
        this.prefix = Objects.requireNonNull(prefix);
        this.daemon = daemon;
        this.threadIndex = new AtomicLong(0);
    }

    /**
     * @since 1.6
     */
    public static CountingThreadFactory factory(String prefix) {
        return new CountingThreadFactory(prefix, false);
    }

    /**
     * @since 1.6
     */
    public static CountingThreadFactory daemonFactory(String prefix) {
        return new CountingThreadFactory(prefix, true);
    }

    @Override
    public Thread newThread(@NonNull Runnable r) {
        Thread t = new Thread(r, newThreadName());
        if (daemon) {
            t.setDaemon(true);
        }
        return t;
    }

    @SuppressLint("DefaultLocale")
    private String newThreadName() {
        return String.format("%s-%d", prefix, threadIndex.getAndIncrement());
    }
}
