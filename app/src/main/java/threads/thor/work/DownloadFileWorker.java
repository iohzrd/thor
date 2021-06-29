package threads.thor.work;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import threads.lite.IPFS;
import threads.lite.utils.ReaderProgress;
import threads.LogUtils;
import threads.thor.MainActivity;
import threads.thor.R;
import threads.thor.core.Content;
import threads.thor.services.ThorService;

public class DownloadFileWorker extends Worker {

    private static final String TAG = DownloadFileWorker.class.getSimpleName();
    private final NotificationManager mNotificationManager;
    private final AtomicReference<Notification> mLastNotification = new AtomicReference<>(null);


    @SuppressWarnings("WeakerAccess")
    public DownloadFileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
        mNotificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel(context);
    }

    private static OneTimeWorkRequest getWork(@NonNull Uri uri, @NonNull Uri source,
                                              @NonNull String filename, @NonNull String mimeType,
                                              long size) {
        Data.Builder data = new Data.Builder();
        data.putString(Content.URI, uri.toString());
        data.putString(Content.NAME, filename);
        data.putString(Content.TYPE, mimeType);
        data.putLong(Content.SIZE, size);
        data.putString(Content.FILE, source.toString());

        return new OneTimeWorkRequest.Builder(DownloadFileWorker.class)
                .setInputData(data.build())
                .setInitialDelay(1, TimeUnit.MILLISECONDS)
                .build();
    }

    public static void download(@NonNull Context context, @NonNull Uri uri, @NonNull Uri source,
                                @NonNull String filename, @NonNull String mimeType, long size) {
        WorkManager.getInstance(context).enqueue(getWork(uri, source, filename, mimeType, size));
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
    private ForegroundInfo createForegroundInfo(@NonNull String title) {
        Notification notification = createNotification(title, 0);
        mLastNotification.set(notification);
        return new ForegroundInfo(getId().hashCode(), notification);
    }

    @Override
    public void onStopped() {
        closeNotification();
    }


    @NonNull
    @Override
    public Result doWork() {


        String dest = getInputData().getString(Content.URI);
        Objects.requireNonNull(dest);
        long start = System.currentTimeMillis();
        LogUtils.info(TAG, " start ... " + dest);


        try {

            Uri uriDest = Uri.parse(dest);
            DocumentFile doc = DocumentFile.fromTreeUri(getApplicationContext(), uriDest);
            Objects.requireNonNull(doc);


            long size = getInputData().getLong(Content.SIZE, 0);
            String name = getInputData().getString(Content.NAME);
            Objects.requireNonNull(name);
            String mimeType = getInputData().getString(Content.TYPE);
            Objects.requireNonNull(mimeType);

            String url = getInputData().getString(Content.FILE);
            Objects.requireNonNull(url);
            Uri uri = Uri.parse(url);

            if (Objects.equals(uri.getScheme(), Content.HTTPS) ||
                    Objects.equals(uri.getScheme(), Content.HTTP)) {
                ForegroundInfo foregroundInfo = createForegroundInfo(name);
                setForegroundAsync(foregroundInfo);
            }


            if (Objects.equals(uri.getScheme(), Content.HTTPS) ||
                    Objects.equals(uri.getScheme(), Content.HTTP)) {
                try {
                    HttpURLConnection.setFollowRedirects(false);

                    HttpURLConnection huc;
                    URL urlCon = new URL(uri.toString());
                    huc = (HttpURLConnection) urlCon.openConnection();
                    huc.setReadTimeout(30000);
                    huc.connect();

                    try (InputStream is = huc.getInputStream()) {
                        DocumentFile child = doc.createFile(mimeType, name);
                        Objects.requireNonNull(child);
                        try (OutputStream os = getApplicationContext().
                                getContentResolver().openOutputStream(child.getUri())) {
                            Objects.requireNonNull(os);
                            IPFS.copy(is, os, new ReaderProgress() {
                                @Override
                                public long getSize() {
                                    return size;
                                }

                                @Override
                                public void setProgress(int progress) {
                                    reportProgress(getId().hashCode(), name, progress);
                                }

                                @Override
                                public boolean doProgress() {
                                    return !isStopped();
                                }

                                @Override
                                public boolean isClosed() {
                                    return !isStopped();
                                }

                            });
                        }

                        if (isStopped()) {
                            child.delete();
                        } else {
                            closeNotification();
                            buildCompleteNotification(name, uriDest);
                        }
                    }


                } catch (Throwable e) {
                    if (!isStopped()) {
                        buildFailedNotification(name);
                    }
                    throw e;
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }

        return Result.success();

    }


    private void closeNotification() {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(getId().hashCode());
        }
    }


    private void buildFailedNotification(@NonNull String name) {

        Notification.Builder builder = new Notification.Builder(
                getApplicationContext(), ThorService.CHANNEL_ID);

        builder.setContentTitle(getApplicationContext().getString(R.string.download_failed, name));
        builder.setSmallIcon(R.drawable.download);
        Intent defaultIntent = new Intent(getApplicationContext(), MainActivity.class);

        int requestID = (int) System.currentTimeMillis();
        PendingIntent defaultPendingIntent = PendingIntent.getActivity(
                getApplicationContext(), requestID, defaultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        builder.setContentIntent(defaultPendingIntent);
        builder.setAutoCancel(true);
        Notification notification = builder.build();

        NotificationManager notificationManager = (NotificationManager)
                getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(TAG.hashCode(), notification);
        }
    }


    private void reportProgress(int idx, @NonNull String info, int percent) {

        if (!isStopped()) {

            Notification notification = createNotification(info, percent);

            if (mNotificationManager != null) {
                mNotificationManager.notify(idx, notification);
            }

        }
    }


    private Notification createNotification(@NonNull String title, int progress) {

        Notification.Builder builder;
        if (mLastNotification.get() != null) {
            builder = Notification.Builder.recoverBuilder(
                    getApplicationContext(), mLastNotification.get());
            builder.setProgress(100, progress, false);
            builder.setContentTitle(title);
            builder.setSubText("" + progress + "%");
            return builder.build();
        } else {
            builder = new Notification.Builder(getApplicationContext(), ThorService.CHANNEL_ID);
        }

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

        builder.setContentTitle(title)
                .setSubText("" + progress + "%")
                .setContentIntent(pendingIntent)
                .setProgress(100, progress, false)
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.drawable.download)
                .addAction(action)
                .setColor(ContextCompat.getColor(getApplicationContext(), android.R.color.black))
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setUsesChronometer(true)
                .setOngoing(true);

        return builder.build();
    }

    private void buildCompleteNotification(@NonNull String name, @NonNull Uri uri) {

        Notification.Builder builder = new Notification.Builder(
                getApplicationContext(), ThorService.CHANNEL_ID);

        builder.setContentTitle(getApplicationContext().getString(R.string.download_complete, name));
        builder.setSmallIcon(R.drawable.download);

        Intent defaultIntent = new Intent(MainActivity.SHOW_DOWNLOADS, uri,
                getApplicationContext(), MainActivity.class);
        int requestID = (int) System.currentTimeMillis();
        PendingIntent defaultPendingIntent = PendingIntent.getActivity(
                getApplicationContext(), requestID, defaultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        builder.setContentIntent(defaultPendingIntent);
        builder.setAutoCancel(true);
        Notification notification = builder.build();

        NotificationManager notificationManager = (NotificationManager)
                getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(TAG.hashCode(), notification);
        }
    }
}
