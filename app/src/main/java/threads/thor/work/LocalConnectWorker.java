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
import java.util.concurrent.TimeUnit;

import io.LogUtils;
import io.ipfs.IPFS;
import threads.thor.core.Content;

public class LocalConnectWorker extends Worker {

    private static final String WID = "SCW";
    private static final String TAG = LocalConnectWorker.class.getSimpleName();


    @SuppressWarnings("WeakerAccess")
    public LocalConnectWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    private static String getUniqueId(@NonNull String pid) {
        return WID + pid;
    }

    private static OneTimeWorkRequest getWork(@NonNull String pid,
                                              @NonNull String host, int port, boolean inet6) {

        Data.Builder data = new Data.Builder();
        data.putString(Content.PID, pid);
        data.putString(Content.HOST, host);
        data.putInt(Content.PORT, port);
        data.putBoolean(Content.INET6, inet6);

        return new OneTimeWorkRequest.Builder(LocalConnectWorker.class)
                .setInputData(data.build())
                .setInitialDelay(5, TimeUnit.SECONDS)
                .addTag(TAG)
                .build();
    }

    public static void connect(@NonNull Context context, @NonNull String pid,
                               @NonNull String host, int port, boolean inet6) {

        WorkManager.getInstance(context).enqueueUniqueWork(
                getUniqueId(pid), ExistingWorkPolicy.KEEP, getWork(pid, host, port, inet6));
    }


    @NonNull
    @Override
    public Result doWork() {

        String pid = getInputData().getString(Content.PID);
        Objects.requireNonNull(pid);
        String host = getInputData().getString(Content.HOST);
        int port = getInputData().getInt(Content.PORT, 0);
        boolean inet6 = getInputData().getBoolean(Content.INET6, false);
        long start = System.currentTimeMillis();

        LogUtils.info(TAG, " start connect [" + pid + "]...");

        try {
            IPFS ipfs = IPFS.getInstance(getApplicationContext());

            String pre = "/ip4";
                if (inet6) {
                    pre = "/ip6";
                }
                String multiAddress = pre + host + "/tcp/" + port + "/p2p/" + pid;


                ipfs.swarmConnect(multiAddress, 10);

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }

        return Result.success();
    }
}

