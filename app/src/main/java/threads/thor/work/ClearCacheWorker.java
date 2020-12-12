package threads.thor.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

import threads.LogUtils;
import threads.thor.FileProvider;
import threads.thor.ipfs.IPFS;

public class ClearCacheWorker extends Worker {

    private static final String TAG = ClearCacheWorker.class.getSimpleName();

    @SuppressWarnings("WeakerAccess")
    public ClearCacheWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    private static OneTimeWorkRequest getWork() {

        return new OneTimeWorkRequest.Builder(ClearCacheWorker.class)
                .setInitialDelay(1, TimeUnit.MILLISECONDS)
                .build();

    }

    public static void clearCache(@NonNull Context context) {

        WorkManager.getInstance(context).enqueueUniqueWork(
                TAG, ExistingWorkPolicy.REPLACE, getWork());

    }


    @NonNull
    @Override
    public Result doWork() {

        long start = System.currentTimeMillis();

        LogUtils.info(TAG, " start ...");

        try {


            FileProvider fileProvider = FileProvider.getInstance(getApplicationContext());
            fileProvider.cleanImageDir();
            fileProvider.cleanDataDir();

            IPFS.logCacheDir(getApplicationContext());

            return Result.success();

        } catch (Throwable e) {
            LogUtils.error(TAG, e);

            return Result.failure();

        } finally {
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }
    }

}

