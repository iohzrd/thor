package threads.thor.utils;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import threads.LogUtils;
import threads.thor.R;

public class AdBlocker {
    private static final String TAG = AdBlocker.class.getSimpleName();
    private static final Set<String> AD_HOSTS = new HashSet<>();

    public static void init(Context context) {

        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.submit(() -> {
            try {
                loadRawData(context);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });
    }

    private static void loadRawData(@NonNull Context context) {
        Objects.requireNonNull(context);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getResources().openRawResource(
                        R.raw.pgl_yoyo_org)))) {
            while (reader.ready()) {
                String line = reader.readLine();
                AD_HOSTS.add(line);
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    public static boolean isAd(@NonNull Uri uri) {
        String host = uri.getHost();
        return isAdHost(host != null ? host : "");
    }

    private static boolean isAdHost(@NonNull String host) {
        if (host.isEmpty()) {
            return false;
        }
        int index = host.indexOf(".");
        return index >= 0 && (AD_HOSTS.contains(host) ||
                index + 1 < host.length() && isAdHost(host.substring(index + 1)));
    }
}
