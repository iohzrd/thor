package threads.thor.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Objects;
import java.util.concurrent.Executors;

import threads.LogUtils;
import threads.thor.Settings;
import threads.thor.core.Content;
import threads.thor.ipfs.IPFS;


public class PageProviderWorker extends Worker {

    public static final String TAG = PageProviderWorker.class.getSimpleName();

    private final IPFS ipfs;


    @SuppressWarnings("WeakerAccess")
    public PageProviderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        ipfs = IPFS.getInstance(context);
    }


    private static OneTimeWorkRequest getWork(@NonNull String cid) {
        Constraints.Builder builder = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED);
        Data.Builder data = new Data.Builder();
        data.putString(Content.CID, cid);

        return new OneTimeWorkRequest.Builder(PageProviderWorker.class)
                .addTag(TAG)
                .setInputData(data.build())
                .setConstraints(builder.build())
                .build();

    }

    public static void providers(@NonNull Context context, @NonNull String cid) {
        WorkManager.getInstance(context).enqueueUniqueWork(
                "PPW" + cid, ExistingWorkPolicy.KEEP, getWork(cid));
    }


    @NonNull
    @Override
    public Result doWork() {

        long start = System.currentTimeMillis();

        LogUtils.info(TAG, "Start " + getId().toString() + " ...");
        String cid = getInputData().getString(Content.CID);
        Objects.requireNonNull(cid);

        try {

            ipfs.dhtFindProviders(cid, pid -> {
                try {
                    LogUtils.error(TAG, "Found Provider " + pid);
                    if (!ipfs.isConnected(pid) && !isStopped()) {
                        Executors.newSingleThreadExecutor().execute(() -> {

                            boolean result = ipfs.swarmConnect(
                                    Content.P2P_PATH + pid, this::isStopped);
                            LogUtils.error(TAG, "Connect " + pid + " " + result);
                        });
                    }

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            }, 10, this::isStopped);

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        } finally {
            LogUtils.info(TAG, "Finish " + getId().toString() +
                    " onStart [" + (System.currentTimeMillis() - start) + "]...");
        }
        return Result.success();
    }

}

