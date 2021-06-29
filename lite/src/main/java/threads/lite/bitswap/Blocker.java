package threads.lite.bitswap;

import androidx.annotation.NonNull;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.Cid;
import threads.lite.core.Closeable;

public class Blocker {
    private static final String TAG = Blocker.class.getSimpleName();


    public void subscribe(@NonNull Cid cid, @NonNull Closeable closeable) {

        String key = "B" + cid.String();
        long start = System.currentTimeMillis();
        synchronized (key.intern()) {
            try {
                // Calling wait() will block this thread until another thread
                // calls notify() on the object.
                LogUtils.verbose(TAG, "Lock " + cid.String());
                Thread thread = new Thread(() -> {
                    try {
                        while (true) {
                            if (closeable.isClosed()) {
                                release(cid);
                                break;
                            }
                            Thread.sleep(25);
                        }
                    } catch (InterruptedException e) {
                        release(cid);
                    }
                });
                thread.start();
                key.intern().wait(IPFS.WANTS_WAIT_TIMEOUT);
                thread.interrupt();
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            } finally {
                LogUtils.verbose(TAG, "Lock Finish " + cid.String() +
                        " took " + (System.currentTimeMillis() - start));
            }
        }
    }

    public void release(@NonNull Cid cid) {
        try {
            String key = "B" + cid.String();
            synchronized (key.intern()) {
                key.intern().notify();
            }
        } catch (Throwable ignore) {
            // ignore
        }
    }

}
