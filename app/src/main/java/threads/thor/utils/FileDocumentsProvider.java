package threads.thor.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;

import androidx.annotation.NonNull;

public class FileDocumentsProvider {

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean hasWritePermission(@NonNull Context context, @NonNull Uri uri) {
        int perm = context.checkUriPermission(uri, Binder.getCallingPid(), Binder.getCallingUid(),
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        return perm != PackageManager.PERMISSION_DENIED;
    }


}