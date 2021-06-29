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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import threads.lite.IPFS;
import threads.lite.cid.Cid;
import threads.lite.core.ClosedException;
import threads.lite.utils.Link;
import threads.lite.core.Progress;
import threads.LogUtils;
import threads.thor.MainActivity;
import threads.thor.R;
import threads.thor.core.Content;
import threads.thor.core.DOCS;
import threads.thor.services.MimeTypeService;
import threads.thor.services.ThorService;
import threads.thor.utils.MimeType;

public class DownloadContentWorker extends Worker {

    private static final String TAG = DownloadContentWorker.class.getSimpleName();
    private final NotificationManager mNotificationManager;
    private final AtomicReference<Notification> mLastNotification = new AtomicReference<>(null);
    private final IPFS ipfs;
    private final DOCS docs;
    private final AtomicBoolean success = new AtomicBoolean(true);

    @SuppressWarnings("WeakerAccess")
    public DownloadContentWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
        ipfs = IPFS.getInstance(context);
        docs = DOCS.getInstance(context);
        mNotificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel(context);
    }

    private static OneTimeWorkRequest getWork(@NonNull Uri uri, @NonNull Uri content) {

        Data.Builder data = new Data.Builder();
        data.putString(Content.URI, uri.toString());
        data.putString(Content.ADDR, content.toString());

        return new OneTimeWorkRequest.Builder(DownloadContentWorker.class)
                .setInputData(data.build())
                .setInitialDelay(1, TimeUnit.MILLISECONDS)
                .build();
    }

    public static void download(@NonNull Context context, @NonNull Uri uri, @NonNull Uri content) {
        WorkManager.getInstance(context).enqueue(getWork(uri, content));
    }

    @Override
    public void onStopped() {
        closeNotification();
    }

    @NonNull
    private ForegroundInfo createForegroundInfo(@NonNull String title) {
        Notification notification = createNotification(title, 0);
        mLastNotification.set(notification);
        return new ForegroundInfo(getId().hashCode(), notification);
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


            String url = getInputData().getString(Content.ADDR);
            Objects.requireNonNull(url);
            Uri uri = Uri.parse(url);
            String name = docs.getFileName(uri);

            if (Objects.equals(uri.getScheme(), Content.IPFS) ||
                    Objects.equals(uri.getScheme(), Content.IPNS)) {
                ForegroundInfo foregroundInfo = createForegroundInfo(name);
                setForegroundAsync(foregroundInfo);
            }


            if (Objects.equals(uri.getScheme(), Content.IPNS) ||
                    Objects.equals(uri.getScheme(), Content.IPFS)) {

                try {

                    Cid content = docs.getContent(uri, this::isStopped);
                    Objects.requireNonNull(content);
                    String mimeType = docs.getMimeType(getApplicationContext(),
                            uri, content, this::isStopped);

                    if (Objects.equals(mimeType, MimeType.DIR_MIME_TYPE)) {
                        doc = doc.createDirectory(name);
                        Objects.requireNonNull(doc);
                    }

                    downloadContent(doc, content, mimeType, name);


                    if (!isStopped()) {
                        closeNotification();
                        if (success.get()) {
                            buildCompleteNotification(name, uriDest);
                        } else {
                            buildFailedNotification(name);
                        }
                    }

                } catch (Throwable e) {
                    if (!isStopped()) {
                        buildFailedNotification(name);
                    }
                    throw e;
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        } finally {
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }

        return Result.success();

    }


    private void downloadContent(@NonNull DocumentFile doc, @NonNull Cid root,
                                 @NonNull String mimeType, @NonNull String name) throws ClosedException {
        downloadLinks(doc, root, mimeType, name);
    }


    private void download(@NonNull DocumentFile doc, @NonNull Cid cid) throws ClosedException {

        long start = System.currentTimeMillis();

        LogUtils.info(TAG, " start [" + (System.currentTimeMillis() - start) + "]...");


        String name = doc.getName();
        Objects.requireNonNull(name);

        if (!ipfs.isDir(cid, this::isStopped)) {

            try (InputStream is = ipfs.getLoaderStream(cid, new Progress() {
                @Override
                public boolean isClosed() {
                    return isStopped();
                }

                @Override
                public void setProgress(int percent) {
                    reportProgress(name, percent);
                }

                @Override
                public boolean doProgress() {
                    return !isStopped();
                }


            })) {
                Objects.requireNonNull(is);
                try (OutputStream os = getApplicationContext().
                        getContentResolver().openOutputStream(doc.getUri())) {
                    Objects.requireNonNull(os);

                    IPFS.copy(is, os);

                }
            } catch (Throwable throwable) {
                success.set(false);

                try {
                    if (doc.exists()) {
                        doc.delete();
                    }
                } catch (Throwable e) {
                    LogUtils.error(TAG, e);
                }

                LogUtils.error(TAG, throwable);
            } finally {
                LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
            }
        }

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


    private void reportProgress(@NonNull String info, int percent) {

        if (!isStopped()) {

            Notification notification = createNotification(info, percent);

            if (mNotificationManager != null) {
                mNotificationManager.notify(getId().hashCode(), notification);
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


    private void evalLinks(@NonNull DocumentFile doc, @NonNull List<Link> links) throws ClosedException {

        for (Link link : links) {
            if (!isStopped()) {
                Cid cid = Cid.decode(link.getContent());
                if (ipfs.isDir(cid, this::isStopped)) {
                    DocumentFile dir = doc.createDirectory(link.getName());
                    Objects.requireNonNull(dir);
                    downloadLinks(dir, cid, MimeType.DIR_MIME_TYPE, link.getName());
                } else {
                    String mimeType = MimeTypeService.getMimeType(link.getName());
                    download(Objects.requireNonNull(doc.createFile(mimeType, link.getName())),
                            cid);
                }
            }
        }

    }


    private void downloadLinks(@NonNull DocumentFile doc,
                               @NonNull Cid cid,
                               @NonNull String mimeType,
                               @NonNull String name) throws ClosedException {


        List<Link> links = ipfs.links(cid, this::isStopped);

        if (links != null) {
            if (links.isEmpty()) {
                if (!isStopped()) {
                    DocumentFile child = doc.createFile(mimeType, name);
                    Objects.requireNonNull(child);
                    download(child, cid);
                }
            } else {
                evalLinks(doc, links);
            }
        }

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
