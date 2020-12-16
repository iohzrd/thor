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
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import threads.LogUtils;
import threads.thor.MainActivity;
import threads.thor.R;
import threads.thor.bt.Bt;
import threads.thor.bt.IdentityService;
import threads.thor.bt.event.EventBus;
import threads.thor.bt.magnet.MagnetUri;
import threads.thor.bt.magnet.MagnetUriParser;
import threads.thor.bt.net.PeerId;
import threads.thor.bt.runtime.BtClient;
import threads.thor.bt.runtime.BtRuntime;
import threads.thor.bt.runtime.Config;
import threads.thor.core.Content;
import threads.thor.core.events.EVENTS;
import threads.thor.ipfs.IPFS;
import threads.thor.services.ThorService;
import threads.thor.utils.ContentStorage;
import threads.thor.utils.Network;

public class DownloadMagnetWorker extends Worker {

    private static final String TAG = DownloadMagnetWorker.class.getSimpleName();
    private final NotificationManager mNotificationManager;
    private final AtomicReference<Notification> mLastNotification = new AtomicReference<>(null);

    @SuppressWarnings("WeakerAccess")
    public DownloadMagnetWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        mNotificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel(context);
    }

    private static OneTimeWorkRequest getWork(@NonNull Uri magnet, @NonNull Uri dest) {

        Constraints.Builder builder = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED);


        Data.Builder data = new Data.Builder();
        data.putString(Content.MAGNET, magnet.toString());
        data.putString(Content.URI, dest.toString());

        return new OneTimeWorkRequest.Builder(DownloadMagnetWorker.class)
                .setInputData(data.build())
                .setConstraints(builder.build())
                .build();

    }

    public static void download(@NonNull Context context, @NonNull Uri magnet, @NonNull Uri dest) {

        if (!Network.isConnected(context)) {
            EVENTS.getInstance(context).warning(context.getString(R.string.offline_mode));
        }
        WorkManager.getInstance(context).enqueue(getWork(magnet, dest));
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

    @Override
    public void onStopped() {
        closeNotification();
    }

    @NonNull
    @Override
    public Result doWork() {

        long start = System.currentTimeMillis();

        LogUtils.info(TAG, " start [" + (System.currentTimeMillis() - start) + "]...");

        try {

            String magnet = getInputData().getString(Content.MAGNET);
            Objects.requireNonNull(magnet);
            String dest = getInputData().getString(Content.URI);
            Objects.requireNonNull(dest);


            MagnetUri magnetUri = MagnetUriParser.lenientParser().parse(magnet);

            String name = magnet;
            if (magnetUri.getDisplayName().isPresent()) {
                name = magnetUri.getDisplayName().get();
            }


            ForegroundInfo foregroundInfo = createForegroundInfo(name);
            setForegroundAsync(foregroundInfo);


            Uri uri = Uri.parse(dest);
            DocumentFile rootDocFile = DocumentFile.fromTreeUri(getApplicationContext(), uri);
            Objects.requireNonNull(rootDocFile);


            DocumentFile find = rootDocFile.findFile(name);
            DocumentFile rootDoc;
            if (find != null && find.exists() && find.isDirectory()) {
                rootDoc = find;
            } else {
                rootDoc = rootDocFile.createDirectory(name);
            }


            try {
                Objects.requireNonNull(rootDoc);
                Config config = new Config();
                config.setNumOfHashingThreads(Runtime.getRuntime().availableProcessors() * 2);
                config.setAcceptorPort(IPFS.nextFreePort());
                byte[] id = new IdentityService().getID();
                config.setLocalPeerId(PeerId.fromBytes(id));


                EventBus eventBus = BtRuntime.provideEventBus();
                ContentStorage storage = new ContentStorage(
                        getApplicationContext(), eventBus, rootDoc);
                BtRuntime runtime = new BtRuntime(
                        getApplicationContext(), config, eventBus);

                BtClient client = Bt.client()
                        .runtime(runtime)
                        .config(config)
                        .storage(storage)
                        .magnet(magnet)
                        .stopWhenDownloaded()
                        .build();

                AtomicInteger atomicProgress = new AtomicInteger(0);
                String finalName = name;
                client.startAsync((torrentSessionState) -> {

                    long completePieces = torrentSessionState.getPiecesComplete();
                    long totalPieces = torrentSessionState.getPiecesTotal();
                    int progress = (int) ((completePieces * 100.0f) / totalPieces);

                    LogUtils.info(TAG, "progress : " + progress +
                            " pieces : " + completePieces + "/" + totalPieces);

                    if (atomicProgress.getAndSet(progress) < progress) {
                        reportProgress(getId().hashCode(), finalName, progress);
                    }
                    if (isStopped()) {
                        try {
                            client.stop();
                        } catch (Throwable e) {
                            LogUtils.error(TAG, e);
                        } finally {
                            LogUtils.info(TAG, "Client is stopped !!!");
                        }
                    }
                }, 1000).join();

                if (!isStopped()) {
                    storage.finish();
                    closeNotification();
                    buildCompleteNotification(name, uri);
                } else {
                    if (rootDoc.exists()) {
                        rootDoc.delete();
                    }
                }


            } catch (Throwable e) {
                if (!isStopped()) {
                    buildFailedNotification(name);
                }
                throw e;
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


    private void reportProgress(int idx, @NonNull String title, int percent) {

        if (!isStopped()) {
            Notification notification = createNotification(title, percent);

            if (mNotificationManager != null) {
                mNotificationManager.notify(idx, notification);
            }
        }
    }


    private Notification createNotification(@NonNull String content, int progress) {


        Notification.Builder builder;
        if (mLastNotification.get() != null) {
            builder = Notification.Builder.recoverBuilder(
                    getApplicationContext(), mLastNotification.get());
            builder.setProgress(100, progress, false);
            builder.setContentTitle(content);
            builder.setSubText("" + progress + "%");
            return builder.build();
        } else {
            builder = new Notification.Builder(getApplicationContext(),
                    ThorService.CHANNEL_ID);


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

            builder.setContentTitle(content)
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


    }

    @NonNull
    private ForegroundInfo createForegroundInfo(@NonNull String title) {
        Notification notification = createNotification(title, 0);
        mLastNotification.set(notification);
        return new ForegroundInfo(getId().hashCode(), notification);
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
}
