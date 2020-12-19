package threads.thor.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import threads.LogUtils;
import threads.thor.Settings;
import threads.thor.core.Content;
import threads.thor.ipfs.IPFS;


public class ThorService {
    public static final String CHANNEL_ID = "CHANNEL_ID";
    private static final String TAG = ThorService.class.getSimpleName();

    private static final String APP_KEY = "AppKey";
    private static final String MAGNET_KEY = "magnetKey";
    private static final String CONTENT_KEY = "contentKey";

    @NonNull
    public static String getDefaultHomepage() {
        return "https://start.duckduckgo.com/";
    }

    @NonNull
    public static String getFileName(@NonNull Uri uri) {

        List<String> paths = uri.getPathSegments();
        if (!paths.isEmpty()) {
            return paths.get(paths.size() - 1);
        } else {
            return "" + uri.getHost();
        }

    }


    public static WebResourceResponse getProxyResponse(@NonNull WebResourceRequest request,
                                                       @NonNull String urlString) throws Throwable {
        Proxy proxy = new Proxy(Proxy.Type.SOCKS,
                new InetSocketAddress(Settings.LOCALHOST, Settings.SOCKSPort));


        HttpURLConnection connection = (HttpURLConnection)
                new URL(urlString).openConnection(proxy);


        connection.setRequestMethod(request.getMethod());
        for (Map.Entry<String, String> requestHeader : request.getRequestHeaders().entrySet()) {
            connection.setRequestProperty(requestHeader.getKey(), requestHeader.getValue());
        }


        String encoding = connection.getContentEncoding();

        connection.getHeaderFields();
        Map<String, String> responseHeaders = new HashMap<>();
        for (String key : connection.getHeaderFields().keySet()) {
            responseHeaders.put(key, connection.getHeaderField(key));
        }

        String connectionType = connection.getContentType();
        String mimeType = "text/plain";
        if (connectionType != null && !connectionType.isEmpty()) {
            mimeType = connectionType.split(";")[0];
        }

        int statusCode = connection.getResponseCode();
        String response = connection.getResponseMessage();

        LogUtils.error(TAG, "" + statusCode);
        LogUtils.error(TAG, response);
        LogUtils.error(TAG, connectionType);
        LogUtils.error(TAG, responseHeaders.toString());
        LogUtils.error(TAG, mimeType);
        LogUtils.error(TAG, encoding);

        if (statusCode == 301 || statusCode == 302) {
            String newUri = responseHeaders.get("Location");
            Objects.requireNonNull(newUri);
            return getProxyResponse(request, newUri);
        }

        InputStream in = null;
        try {
            in = new BufferedInputStream(connection.getInputStream());
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        return new WebResourceResponse(mimeType, encoding, statusCode,
                response, responseHeaders, in);
    }


    @Nullable
    public static Uri getContentUri(@NonNull Context context) {
        Objects.requireNonNull(context);
        SharedPreferences sharedPref = context.getSharedPreferences(
                APP_KEY, Context.MODE_PRIVATE);
        String content = sharedPref.getString(CONTENT_KEY, null);
        if (content != null) {
            return Uri.parse(content);
        }
        return null;
    }

    public static void setContentUri(@NonNull Context context, @NonNull Uri contentUri) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(contentUri);
        SharedPreferences sharedPref = context.getSharedPreferences(
                APP_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(CONTENT_KEY, contentUri.toString());
        editor.apply();

    }

    @Nullable
    public static String getMagnet(@NonNull Context context) {
        Objects.requireNonNull(context);
        SharedPreferences sharedPref = context.getSharedPreferences(
                APP_KEY, Context.MODE_PRIVATE);
        return sharedPref.getString(MAGNET_KEY, null);
    }

    public static void setMagnet(@NonNull Context context, @NonNull String magnet) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(magnet);
        SharedPreferences sharedPref = context.getSharedPreferences(
                APP_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(MAGNET_KEY, magnet);
        editor.apply();

    }

    @NonNull
    public static String loadRawData(@NonNull Context context, @RawRes int id) {
        Objects.requireNonNull(context);

        try (InputStream inputStream = context.getResources().openRawResource(id)) {
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

                IPFS.copy(inputStream, outputStream);
                return new String(outputStream.toByteArray());

            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            return "";
        }
    }


    public static void setFileInfo(@NonNull Context context, @NonNull Uri uri,
                                   @NonNull String filename, @NonNull String mimeType,
                                   long size) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(uri);
        Objects.requireNonNull(filename);
        Objects.requireNonNull(mimeType);
        SharedPreferences sharedPref = context.getSharedPreferences(
                APP_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        editor.putString(Content.INFO + Content.NAME, filename);
        editor.putString(Content.INFO + Content.TYPE, mimeType);
        editor.putLong(Content.INFO + Content.SIZE, size);
        editor.putString(Content.INFO + Content.URI, uri.toString());
        editor.apply();
    }

    @NonNull
    public static FileInfo getFileInfo(@NonNull Context context) {

        Objects.requireNonNull(context);
        SharedPreferences sharedPref = context.getSharedPreferences(
                APP_KEY, Context.MODE_PRIVATE);
        String filename = sharedPref.getString(Content.INFO + Content.NAME, null);
        Objects.requireNonNull(filename);
        String mimeType = sharedPref.getString(Content.INFO + Content.TYPE, null);
        Objects.requireNonNull(mimeType);
        String uri = sharedPref.getString(Content.INFO + Content.URI, null);
        Objects.requireNonNull(uri);
        long size = sharedPref.getLong(Content.INFO + Content.SIZE, 0L);

        return new FileInfo(Uri.parse(uri), filename, mimeType, size);
    }

    public static class FileInfo {
        @NonNull
        private final Uri uri;
        @NonNull
        private final String filename;
        @NonNull
        private final String mimeType;

        private final long size;

        public FileInfo(@NonNull Uri uri, @NonNull String filename,
                        @NonNull String mimeType, long size) {
            this.uri = uri;
            this.filename = filename;
            this.mimeType = mimeType;
            this.size = size;
        }

        @NonNull
        public Uri getUri() {
            return uri;
        }

        @NonNull
        public String getFilename() {
            return filename;
        }

        @NonNull
        public String getMimeType() {
            return mimeType;
        }


        public long getSize() {
            return size;
        }
    }
}
