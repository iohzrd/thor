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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import threads.LogUtils;
import threads.thor.MainActivity;
import threads.thor.R;
import threads.thor.Settings;
import threads.thor.core.Content;
import threads.thor.core.DOCS;
import threads.thor.ipfs.IPFS;
import threads.thor.ipfs.LinkInfo;
import threads.thor.ipfs.Progress;
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
    private ForegroundInfo createForegroundInfo(int idx, @NonNull String title) {
        Notification notification = createNotification(title, 0);
        mLastNotification.set(notification);
        return new ForegroundInfo(idx, notification);
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

            int idx = getId().hashCode();
            String url = getInputData().getString(Content.ADDR);
            Objects.requireNonNull(url);
            Uri uri = Uri.parse(url);
            String name = ThorService.getFileName(uri);

            if (Objects.equals(uri.getScheme(), Content.IPFS) ||
                    Objects.equals(uri.getScheme(), Content.IPNS)) {
                ForegroundInfo foregroundInfo = createForegroundInfo(idx, name);
                setForegroundAsync(foregroundInfo);
            }

            if (Objects.equals(uri.getScheme(), Content.IPFS) ||
                    Objects.equals(uri.getScheme(), Content.IPNS)) {
                if (!isStopped()) {
                    docs.connectUri(uri);
                }
            }

            if (Objects.equals(uri.getScheme(), Content.IPNS) ||
                    Objects.equals(uri.getScheme(), Content.IPFS)) {

                try {

                    DOCS.FileInfo fileInfo = docs.getFileInfo(uri, this::isStopped);


                    String root = fileInfo.getContent();

                    String mimeType = fileInfo.getMimeType();
                    String fileName = fileInfo.getFilename();

                    if (Objects.equals(mimeType, MimeType.DIR_MIME_TYPE)) {
                        doc = doc.createDirectory(fileName);
                        Objects.requireNonNull(doc);
                    }

                    downloadContent(idx, doc, root, mimeType, fileName);


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


    private void downloadContent(int idx, @NonNull DocumentFile doc, @NonNull String root,
                                 @NonNull String mimeType, @NonNull String name) {
        ExecutorService executor = Executors.newFixedThreadPool(1);

        List<DocumentFileContent> works = new ArrayList<>();

        if (!isStopped()) {
            downloadLinks(doc, works, root, mimeType, name);


            ConcurrentLinkedQueue<Future<Boolean>> futures = new ConcurrentLinkedQueue<>();
            for (DocumentFileContent work : works) {
                futures.add(executor.submit(() -> download(idx, work)));
            }


            do {

                for (Future<Boolean> future : futures) {
                    if (future.isDone() || future.isCancelled()) {
                        futures.remove(future);
                    }
                }


                if (isStopped()) {
                    executor.shutdown();
                    executor.shutdownNow();
                    break;
                }

            } while (!futures.isEmpty());
        }
    }


    private boolean download(int idx, @NonNull DocumentFileContent docContent) {

        long start = System.currentTimeMillis();

        LogUtils.info(TAG, " start [" + (System.currentTimeMillis() - start) + "]...");



        String cid = docContent.content;
        DocumentFile doc = docContent.file;

        String name = doc.getName();
        Objects.requireNonNull(name);

        AtomicLong started = new AtomicLong(System.currentTimeMillis());


        if (!ipfs.isEmptyDir(cid)) {

            try (InputStream is = ipfs.getLoaderStream(cid, new Progress() {
                @Override
                public boolean isClosed() {

                    long diff = System.currentTimeMillis() - started.get();
                    boolean abort = (diff > (Settings.IPFS_TIMEOUT));
                    return isStopped() || abort;
                }

                @Override
                public void setProgress(int percent) {

                    reportProgress(idx, name, percent);
                    started.set(System.currentTimeMillis());
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
        return success.get();
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
                getApplicationContext(), requestID, defaultIntent, PendingIntent.FLAG_UPDATE_CURRENT);

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
                main, PendingIntent.FLAG_UPDATE_CURRENT);

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


    private void evalLinks(@NonNull DocumentFile doc,
                           @NonNull List<DocumentFileContent> works,
                           @NonNull List<LinkInfo> links) {

        for (LinkInfo link : links) {
            if (link.isDirectory()) {
                DocumentFile dir = doc.createDirectory(link.getName());
                Objects.requireNonNull(dir);
                downloadLinks(dir, works, link.getContent(), MimeType.DIR_MIME_TYPE, link.getName());
                works.add(new DocumentFileContent(dir, link.getContent()));
            } else {

                String mimeType = MimeTypeService.getMimeType(link.getName());
                works.add(new DocumentFileContent(
                        doc.createFile(mimeType, link.getName()), link.getContent()));
            }
        }

    }


    private void downloadLinks(@NonNull DocumentFile doc,
                               @NonNull List<DocumentFileContent> works,
                               @NonNull String cid,
                               @NonNull String mimeType,
                               @NonNull String name) {


        List<LinkInfo> links = ipfs.getLinks(cid, this::isStopped);

        if (links != null) {
            if (links.isEmpty()) {
                if (!isStopped()) {
                    DocumentFile child = doc.createFile(mimeType, name);
                    Objects.requireNonNull(child);
                    works.add(new DocumentFileContent(child, cid));
                }
            } else {
                evalLinks(doc, works, links);
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
                getApplicationContext(), requestID, defaultIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        builder.setContentIntent(defaultPendingIntent);
        builder.setAutoCancel(true);
        Notification notification = builder.build();

        NotificationManager notificationManager = (NotificationManager)
                getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(TAG.hashCode(), notification);
        }
    }

    public static class DocumentFileContent {
        private final DocumentFile file;
        private final String content;

        public DocumentFileContent(DocumentFile file, String cid) {
            this.file = file;
            this.content = cid;
        }
    }
}
