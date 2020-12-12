package threads.thor.fragments;

import android.net.Uri;
import android.webkit.WebView;

import androidx.annotation.NonNull;

public interface ActionListener {
    void openUri(@NonNull Uri uri);

    WebView getWebView();
}
