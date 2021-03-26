package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.cid.Cid;

public class Blocker {
    private static final String TAG = Blocker.class.getSimpleName();


    public void Subscribe(@NonNull Cid cid) {

        String key = "B" + cid.String();
        long start = System.currentTimeMillis();
        synchronized (key.intern()) {
            try {
                // Calling wait() will block this thread until another thread
                // calls notify() on the object.
                LogUtils.error(TAG, "Lock " + cid.String());
                key.intern().wait(IPFS.WANTS_WAIT_TIMEOUT);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            } finally {
                LogUtils.error(TAG, "Lock Finish " + cid.String() +
                        " took " + (System.currentTimeMillis() - start));
            }
        }
    }

    public void Release(@NonNull Cid cid) {
        try {
            String key = "B" + cid.String();
            synchronized (key.intern()) {
                key.intern().notify();
                LogUtils.error(TAG, "Release " + cid.String());
            }
        } catch (Throwable ignore) {
            // ignore
        }
    }

}
