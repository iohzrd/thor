package threads.thor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Settings {

    public static final String AGENT = "/go-ipfs/0.9.0-dev/thor";
    public static final String HOMEPAGE = "https://start.duckduckgo.com/";
    public static final int TIMEOUT_BOOTSTRAP = 5;
    public static final int MIN_PEERS = 10;
    public static final long RESOLVE_MAX_TIME = 20000; // 20 sec
    public static final int RESOLVE_TIMEOUT = 3000; // 3 sec
    public static final String BLOCKS = "/blocks";
    public static final int PROVIDER_TIMEOUT = 30;
    // IPFS BOOTSTRAP
    @NonNull
    public static final List<String> IPFS_BOOTSTRAP_NODES = new ArrayList<>(Arrays.asList(
            "/ip4/147.75.80.110/tcp/4001/p2p/QmbFgm5zan8P6eWWmeyfncR5feYEMPbht5b1FW1C37aQ7y", // default relay  libp2p
            "/ip4/147.75.195.153/tcp/4001/p2p/QmW9m57aiBDHAkKj9nmFSEn7ZqrcF1fZS4bipsTCHburei",// default relay  libp2p
            "/ip4/147.75.70.221/tcp/4001/p2p/Qme8g49gm3q4Acp7xWBKg3nAa9fxZ1YmyDJdyGgoG6LsXh",// default relay  libp2p

            "/ip4/104.131.131.82/tcp/4001/p2p/QmaCpDMGvV2BGHeYERUEnRQAwe3N8SzbUtfsmvsqQLuvuJ"// mars.i.ipfs.io

    ));
    // IPFS BOOTSTRAP DNS
    public static final String LIB2P_DNS = "_dnsaddr.bootstrap.libp2p.io";
    public static final String DNS_ADDR = "dnsaddr=/dnsaddr/";
    public static final String DNS_LINK = "dnslink=";


    private static final String APP_KEY = "AppKey";
    private static final String JAVASCRIPT_KEY = "javascriptKey";
    private static final String REDIRECT_URL_KEY = "redirectUrlKey";
    private static final String REDIRECT_INDEX_KEY = "redirectIndexKey";

    public static void setJavascriptEnabled(Context context, boolean auto) {
        SharedPreferences sharedPref = context.getSharedPreferences(APP_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(JAVASCRIPT_KEY, auto);
        editor.apply();
    }

    public static boolean isJavascriptEnabled(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(APP_KEY, Context.MODE_PRIVATE);
        return sharedPref.getBoolean(JAVASCRIPT_KEY, true);

    }

    public static void setRedirectUrlEnabled(Context context, boolean auto) {
        SharedPreferences sharedPref = context.getSharedPreferences(APP_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(REDIRECT_URL_KEY, auto);
        editor.apply();
    }

    public static boolean isRedirectUrlEnabled(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(APP_KEY, Context.MODE_PRIVATE);
        return sharedPref.getBoolean(REDIRECT_URL_KEY, false);
    }

    public static void setRedirectIndexEnabled(Context context, boolean auto) {
        SharedPreferences sharedPref = context.getSharedPreferences(APP_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(REDIRECT_INDEX_KEY, auto);
        editor.apply();
    }

    public static boolean isRedirectIndexEnabled(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(APP_KEY, Context.MODE_PRIVATE);
        return sharedPref.getBoolean(REDIRECT_INDEX_KEY, true);

    }

    @SuppressLint("SetJavaScriptEnabled")
    public static void setWebSettings(@NonNull WebView webView, boolean enableJavascript) {


        WebSettings settings = webView.getSettings();
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + ")");


        settings.setJavaScriptEnabled(enableJavascript);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);

        settings.setSafeBrowsingEnabled(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowContentAccess(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setAllowFileAccess(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkLoads(false);
        settings.setBlockNetworkImage(false);
        settings.setDomStorageEnabled(true);
        settings.setAppCacheEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDatabaseEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportMultipleWindows(false);
        settings.setGeolocationEnabled(false);
    }
}
