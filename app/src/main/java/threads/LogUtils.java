package threads;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import threads.thor.BuildConfig;

public class LogUtils {

    @SuppressWarnings("SameReturnValue")
    public static boolean isDebug() {
        return BuildConfig.DEBUG;
    }


    public static void verbose(@Nullable final String tag, @Nullable String message) {
        if (isDebug()) {
            Log.v(tag, "" + message);
        }
    }

    public static void info(@Nullable final String tag, @Nullable String message) {
        if (isDebug()) {
            Log.i(tag, "" + message);
        }
    }

    public static void error(@Nullable final String tag, @Nullable String message) {
        if (isDebug()) {
            Log.e(tag, "" + message);
        }
    }

    public static void error(@Nullable final String tag, @Nullable String message,
                             @NonNull Throwable throwable) {
        if (isDebug()) {
            Log.e(tag, "" + message, throwable);
        }
    }

    public static void error(final String tag, @NonNull Throwable throwable) {
        if (isDebug()) {
            Log.e(tag, "" + throwable.getLocalizedMessage(), throwable);
        }
    }

    public static void debug(@NonNull String tag, @NonNull String message) {
        if (isDebug()) {
            Log.d(tag, message);
        }
    }
}
