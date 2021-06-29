package threads.lite;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


public class LogUtils {
    public static final String TAG = LogUtils.class.getSimpleName();

    @SuppressWarnings("SameReturnValue")
    public static boolean isDebug() {
        return true;
    }

    public static void verbose(@Nullable final String tag, @Nullable String message) {
        if (isDebug()) {
            Log.v(tag, "" + message);
        }
    }

    public static void warning(@Nullable final String tag, @Nullable String message) {
        if (isDebug()) {
            Log.w(tag, "" + message);
        }
    }

    public static void info(@Nullable final String tag, @Nullable String message) {
        if (isDebug()) {
            Log.i(tag, "" + message);
        }
    }


    public static void debug(@Nullable final String tag, @Nullable String message) {
        if (isDebug()) {
            Log.d(tag, "" + message);
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

    public static void error(final String tag, @Nullable Throwable throwable) {
        if (isDebug()) {
            if (throwable != null) {
                Log.e(tag, "" + throwable.getLocalizedMessage(), throwable);
            } else {
                Log.e(tag, "no throwable");
            }
        }
    }
}
