package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;

public class Blocker {
    private static final String TAG = Blocker.class.getSimpleName();


    public void Subscribe(@NonNull Cid cid, @NonNull Closeable closeable) {

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
                                Release(cid);
                                break;
                            }
                            Thread.sleep(25);
                        }
                    } catch (InterruptedException e) {
                        Release(cid);
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

    public void Release(@NonNull Cid cid) {
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
