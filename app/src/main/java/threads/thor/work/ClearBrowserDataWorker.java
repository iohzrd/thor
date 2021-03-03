package threads.thor.work;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ForegroundInfo;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.util.concurrent.TimeUnit;

import threads.LogUtils;
import threads.thor.FileProvider;
import threads.thor.MainActivity;
import threads.thor.R;
import threads.thor.core.blocks.BLOCKS;
import threads.thor.core.pages.PAGES;
import threads.thor.services.ThorService;

public class ClearBrowserDataWorker extends Worker {

    private static final String TAG = ClearBrowserDataWorker.class.getSimpleName();
    private final NotificationManager mNotificationManager;

    @SuppressWarnings("WeakerAccess")
    public ClearBrowserDataWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        mNotificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel(context);
    }

    private static OneTimeWorkRequest getWork() {

        return new OneTimeWorkRequest.Builder(ClearBrowserDataWorker.class)
                .setInitialDelay(1, TimeUnit.MILLISECONDS)
                .build();

    }

    public static void clearCache(@NonNull Context context) {

        WorkManager.getInstance(context).enqueueUniqueWork(
                TAG, ExistingWorkPolicy.REPLACE, getWork());

    }

    public static void deleteCache(@NonNull Context context) {
        try {
            File dir = context.getCacheDir();
            deleteDir(dir);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public static boolean deleteDir(@Nullable File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if(children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) {
                        return false;
                    }
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }

    public static void logCacheDir(@NonNull Context context) {
        try {
            File[] files = context.getCacheDir().listFiles();
            if (files != null) {
                for (File file : files) {
                    LogUtils.error(TAG, "" + file.length() + " " + file.getAbsolutePath());
                    if (file.isDirectory()) {
                        File[] children = file.listFiles();
                        if (children != null) {
                            for (File child : children) {
                                LogUtils.error(TAG, "" + child.length() + " " + child.getAbsolutePath());
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    private Notification createNotification() {

        Notification.Builder builder = new Notification.Builder(getApplicationContext(),
                ThorService.CHANNEL_ID);


        PendingIntent intent = WorkManager.getInstance(getApplicationContext())
                .createCancelPendingIntent(getId());
        String cancel = getApplicationContext().getString(android.R.string.cancel);

        Intent main = new Intent(getApplicationContext(), MainActivity.class);

        int requestID = (int) System.currentTimeMillis();
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), requestID,
                main, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Action action = new Notification.Action.Builder(
                Icon.createWithResource(getApplicationContext(), R.drawable.pause), cancel,
                intent).build();

        builder.setContentTitle(getApplicationContext().getString(R.string.clear_cache_and_cookies))
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.drawable.refresh)
                .addAction(action)
                .setColor(ContextCompat.getColor(getApplicationContext(), android.R.color.black))
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setUsesChronometer(true);

        return builder.build();
    }

    @NonNull
    private ForegroundInfo createForegroundInfo() {
        Notification notification = createNotification();
        return new ForegroundInfo(getId().hashCode(), notification);
    }

    @Override
    public void onStopped() {
        closeNotification();
    }

    private void closeNotification() {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(getId().hashCode());
        }
    }

    private void createChannel(@NonNull Context context) {

        try {
            CharSequence name = context.getString(R.string.channel_name);
            String description = context.getString(R.string.channel_description);
            NotificationChannel mChannel = new NotificationChannel(ThorService.CHANNEL_ID, name,
                    NotificationManager.IMPORTANCE_HIGH);
            mChannel.setDescription(description);

            if (mNotificationManager != null) {
                mNotificationManager.createNotificationChannel(mChannel);
            }

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

    }

    @NonNull
    @Override
    public Result doWork() {

        long start = System.currentTimeMillis();

        LogUtils.info(TAG, " start ...");

        try {
            ForegroundInfo foregroundInfo = createForegroundInfo();
            setForegroundAsync(foregroundInfo);

            // Clear all the cookies
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();

            // Clear local data
            FileProvider fileProvider = FileProvider.getInstance(getApplicationContext());
            fileProvider.cleanImageDir();
            fileProvider.cleanDataDir();

            // Clear ipfs and pages data
            BLOCKS.getInstance(getApplicationContext()).clear();
            PAGES.getInstance(getApplicationContext()).clear();

            deleteCache(getApplicationContext());
            logCacheDir(getApplicationContext());

        } catch (Throwable e) {
            LogUtils.error(TAG, e);

        } finally {
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }

        return Result.success();
    }
}

