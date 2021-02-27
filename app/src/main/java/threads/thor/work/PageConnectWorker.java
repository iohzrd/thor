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

import thor.Peer;
import threads.LogUtils;
import threads.thor.core.Content;
import threads.thor.core.pages.PAGES;
import threads.thor.core.pages.Page;
import threads.thor.ipfs.IPFS;


public class PageConnectWorker extends Worker {

    private static final String WID = "PCW";
    public static final String TAG = PageConnectWorker.class.getSimpleName();


    @SuppressWarnings("WeakerAccess")
    public PageConnectWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    private static String getUniqueId(@NonNull String pid) {
        return WID + pid;
    }

    private static OneTimeWorkRequest getWork(@NonNull String pid) {
        Constraints.Builder builder = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED);

        Data.Builder data = new Data.Builder();
        data.putString(Content.PID, pid);

        return new OneTimeWorkRequest.Builder(PageConnectWorker.class)
                .setInputData(data.build())
                .setConstraints(builder.build())
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
            PAGES pages = PAGES.getInstance(getApplicationContext());
            Page page = pages.getPage(pid);
            boolean connected = ipfs.isConnected(pid);
            if (!connected) {
                if (page != null) {
                    String address = page.getAddress();
                    if (!address.isEmpty()) {
                        connected = ipfs.swarmConnect(
                                address.concat(Content.P2P_PATH).concat(page.getPid()), 5);
                    }
                }
                if (!connected) {
                    connected = ipfs.swarmConnect(Content.P2P_PATH + pid, 10);
                }
            }

            if (page != null && !isStopped()) {
                Peer info = ipfs.swarmPeer(pid);
                if (info != null) {
                    String address = info.getAddress();
                    if (!address.isEmpty() && !address.contains(Content.CIRCUIT)) {
                        if (!Objects.equals(address, page.getAddress())) {
                            pages.setPageAddress(pid, address);
                            pages.resetBootstrap(pid);
                        } else {
                            pages.incrementRating(pid);
                            // success here, same address
                            if (!page.isBootstrap()) {
                                pages.setBootstrap(pid);
                            }
                        }
                    } else {
                        if (!page.getAddress().isEmpty()) {
                            pages.setPageAddress(pid, "");
                        }
                        if (page.isBootstrap()) {
                            pages.resetBootstrap(pid);
                        }
                    }
                } else {
                    if (!page.getAddress().isEmpty()) {
                        pages.setPageAddress(pid, "");
                    }
                    if (page.isBootstrap()) {
                        pages.resetBootstrap(pid);
                    }
                }

            }

            LogUtils.error(TAG, "Connect " + pid + " " + connected);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            LogUtils.info(TAG, " finish onStart ...");
        }

        return Result.success();
    }
}

