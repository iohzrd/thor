package io.ipfs.bitswap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.ConcurrentHashMap;

import io.LogUtils;
import io.ipfs.cid.Cid;
import io.ipfs.format.Block;

public class Pubsub {

    private static final String TAG = Pubsub.class.getSimpleName();

    private final ConcurrentHashMap<Cid, Block> maps = new ConcurrentHashMap<>();

    @Nullable
    public Block Subscribe(@NonNull Cid cid) {

        synchronized (cid.String().intern()) {
            try {
                // Calling wait() will block this thread until another thread
                // calls notify() on the object.
                LogUtils.error(TAG, "Lock " + cid.String());
                cid.String().intern().wait();
                LogUtils.error(TAG, "Is Released " + cid.String());
                return maps.get(cid);

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                maps.remove(cid);
            }
        }
    }

    public void Release(@NonNull Cid cid) {
        try {
            synchronized (cid.String().intern()) {
                cid.String().intern().notify();
                LogUtils.error(TAG, "Release " + cid.String());
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void Publish(@NonNull Block block) {
        try {
            Cid cid = block.Cid();
            maps.put(cid, block);
            Release(cid);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void clear() {
        maps.clear();
    }
}
