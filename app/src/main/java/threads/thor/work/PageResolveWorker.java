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

import threads.LogUtils;
import threads.thor.core.Content;
import threads.thor.ipfs.IPFS;


public class PageResolveWorker extends Worker {

    private static final String WID = "PRW";
    private static final String TAG = PageResolveWorker.class.getSimpleName();


    @SuppressWarnings("WeakerAccess")
    public PageResolveWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    private static String getUniqueId(@NonNull String name) {
        return WID + name;
    }

    private static OneTimeWorkRequest getWork(@NonNull String name) {
        Constraints.Builder builder = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED);

        Data.Builder data = new Data.Builder();
        data.putString(Content.NAME, name);

        return new OneTimeWorkRequest.Builder(PageResolveWorker.class)
                .setInputData(data.build())
                .setConstraints(builder.build())
                .addTag(TAG)
                .build();
    }

    public static void resolve(@NonNull Context context, @NonNull String name) {

        WorkManager.getInstance(context).enqueueUniqueWork(
                getUniqueId(name), ExistingWorkPolicy.REPLACE, getWork(name));
    }


    @NonNull
    @Override
    public Result doWork() {

        String name = getInputData().getString(Content.NAME);
        Objects.requireNonNull(name);

        try {
            IPFS ipfs = IPFS.getInstance(getApplicationContext());
            ipfs.resolveName(name, this::isStopped);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            LogUtils.info(TAG, " finish onStart ...");
        }

        return Result.success();
    }
}

