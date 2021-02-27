package threads.thor.utils;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;

public class PermissionAction implements View.OnClickListener {


    @Override
    public void onClick(View v) {

        final Intent i = new Intent();
        i.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        i.setData(Uri.parse("package:" + v.getContext().getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        v.getContext().startActivity(i);
    }
}
