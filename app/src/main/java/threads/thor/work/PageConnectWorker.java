package threads.thor.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Objects;

import threads.LogUtils;
import threads.thor.core.Content;
import threads.thor.ipfs.IPFS;


public class PageConnectWorker extends Worker {

    private static final String WID = "PCW";
    private static final String TAG = PageConnectWorker.class.getSimpleName();


    @SuppressWarnings("WeakerAccess")
    public PageConnectWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    private static String getUniqueId(@NonNull String pid) {
        return WID + pid;
    }

    private static OneTimeWorkRequest getWork(@NonNull String pid) {

        Data.Builder data = new Data.Builder();
        data.putString(Content.PID, pid);

        return new OneTimeWorkRequest.Builder(PageConnectWorker.class)
                .setInputData(data.build())
                .addTag(TAG)
                .build();
    }

    public static void connect(@NonNull Context context, @NonNull String pid) {

        WorkManager.getInstance(context).enqueueUniqueWork(
                getUniqueId(pid), ExistingWorkPolicy.KEEP, getWork(pid));
    }


    @NonNull
    @Override
    public Result doWork() {

        String pid = getInputData().getString(Content.PID);
        Objects.requireNonNull(pid);

        try {
            IPFS ipfs = IPFS.getInstance(getApplicationContext());
            boolean connected = ipfs.isConnected(pid);
            if (!connected) {
                connected = ipfs.swarmConnect("/p2p/" + pid, 10);
            }
            LogUtils.error(TAG, "Connect " + pid + " " + connected);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            return Result.failure();
        } finally {
            LogUtils.info(TAG, " finish onStart ...");
        }

        return Result.success();
    }
}

