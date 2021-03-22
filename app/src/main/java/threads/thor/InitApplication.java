package threads.thor;

import android.app.Application;

import java.util.concurrent.Executors;

import io.ipfs.IPFS;
import threads.LogUtils;
import threads.thor.utils.AdBlocker;

public class InitApplication extends Application {

    public static final String TIME_TAG = "TIME_TAG";
    private static final String TAG = InitApplication.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();

        long start = System.currentTimeMillis();

        AdBlocker.init(getApplicationContext());


        LogUtils.info(TIME_TAG, "InitApplication after add blocker [" +
                (System.currentTimeMillis() - start) + "]...");
        try {
            IPFS ipfs = IPFS.getInstance(getApplicationContext());
            ipfs.startDaemon();
            Executors.newSingleThreadExecutor().submit(ipfs::bootstrap);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        LogUtils.info(TIME_TAG, "InitApplication after starting ipfs [" +
                (System.currentTimeMillis() - start) + "]...");

    }

}
