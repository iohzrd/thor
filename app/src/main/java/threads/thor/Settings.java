package threads.thor;

import android.annotation.SuppressLint;
import android.os.Build;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import threads.thor.magnet.net.InetPeerAddress;
import threads.thor.magnet.protocol.crypto.EncryptionPolicy;
import threads.thor.magnet.service.NetworkUtil;

public class Settings {

    public static final int TIMEOUT_BOOTSTRAP = 5;
    public static final int MIN_PEERS = 10;

    // DHT
    public static final int MAX_ENTRIES_PER_BUCKET = 8;
    public static final int MAX_ACTIVE_TASKS = 7;
    public static final int MAX_ACTIVE_CALLS = 256;
    public static final int MAX_CONCURRENT_REQUESTS = 10;
    public static final int RPC_CALL_TIMEOUT_MAX = 10 * 1000;
    public static final int RPC_CALL_TIMEOUT_BASELINE_MIN = 100; // ms
    public static final int BOOTSTRAP_IF_LESS_THAN_X_PEERS = 30;

    public static final int DHT_UPDATE_INTERVAL = 1000;
    public static final int BUCKET_REFRESH_INTERVAL = 15 * 60 * 1000;
    public static final int RECEIVE_BUFFER_SIZE = 5 * 1024;
    public static final int CHECK_FOR_EXPIRED_ENTRIES = 5 * 60 * 1000;
    public static final int MAX_ITEM_AGE = 60 * 60 * 1000;
    public static final int TOKEN_TIMEOUT = 5 * 60 * 1000;
    public static final int MAX_DB_ENTRIES_PER_KEY = 6000;


    // enter survival mode if we don't see new packets after this time
    public static final int REACHABILITY_TIMEOUT = 60 * 1000;
    public static final int BOOTSTRAP_MIN_INTERVAL = 4 * 60 * 1000;
    public static final int USE_BT_ROUTER_IF_LESS_THAN_X_PEERS = 10;
    public static final int SELF_LOOKUP_INTERVAL = 30 * 60 * 1000;
    public static final int RANDOM_LOOKUP_INTERVAL = 10 * 60 * 1000;

    public static final int ANNOUNCE_CACHE_MAX_AGE = 30 * 60 * 1000;
    public static final int ANNOUNCE_CACHE_FAST_LOOKUP_AGE = 8 * 60 * 1000;


    // MAGNET
    public static final int maxPeerConnections = 500;
    public static final int maxPeerConnectionsPerTorrent = 500;
    public static final int transferBlockSize = 16 * 1024;
    public static final int maxIOQueueSize = Integer.MAX_VALUE;
    public static final int maxConcurrentlyActivePeerConnectionsPerTorrent = 10;
    public static final int maxPendingConnectionRequests = 50;
    public static final int metadataExchangeBlockSize = 16 * 1024; // 16 KB
    public static final int metadataExchangeMaxSize = 2 * 1024 * 1024; // 2 MB
    public static final int msePrivateKeySize = 20; // 20 bytes
    public static final int maxOutstandingRequests = 250;
    public static final int networkBufferSize = 1024 * 1024; // 1 MB

    public static final InetAddress acceptorAddress = NetworkUtil.getInetAddressFromNetworkInterfaces();
    public static final Duration peerDiscoveryInterval = Duration.ofSeconds(5);

    public static final Duration peerHandshakeTimeout = Duration.ofSeconds(30);
    public static final Duration peerConnectionInactivityThreshold = Duration.ofMinutes(3);

    public static final Duration maxPieceReceivingTime = Duration.ofSeconds(5);
    public static final Duration maxMessageProcessingInterval = Duration.ofMillis(100);
    public static final Duration unreachablePeerBanDuration = Duration.ofMinutes(30);
    public static final Duration shutdownHookTimeout = Duration.ofSeconds(30);
    public static final Duration timeoutedAssignmentPeerBanDuration = Duration.ofMinutes(1);
    public static final EncryptionPolicy encryptionPolicy = EncryptionPolicy.PREFER_PLAINTEXT;
    public static final int numOfHashingThreads = java.lang.Runtime.getRuntime().availableProcessors() * 2;


    // DHT BOOTSTRAP
    public static final Collection<InetPeerAddress> BOOTSTRAP_NODES = Arrays.asList(
            new InetPeerAddress("router.bittorrent.com", 6881),
            new InetPeerAddress("dht.transmissionbt.com", 6881),
            new InetPeerAddress("router.utorrent.com", 6881)
    );

    // DHT BOOTSTRAP
    public static final InetSocketAddress[] UNRESOLVED_BOOTSTRAP_NODES = new InetSocketAddress[]{
            InetSocketAddress.createUnresolved("dht.transmissionbt.com", 6881),
            InetSocketAddress.createUnresolved("router.bittorrent.com", 6881),
            InetSocketAddress.createUnresolved("router.utorrent.com", 6881)
    };


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

    // TOR CONFIGURATION
    public static final int SOCKSPort = 9050;
    public static final String LOCALHOST = "localhost";
    public static final String DIRECTORY_TOR_DATA = "data";

    public static String getDHTVersion() {
        return "ml" + new String(new byte[]{(byte) (11 >> 8 & 0xFF), (byte) (11 & 0xff)}, StandardCharsets.ISO_8859_1);
    }

    @NonNull
    public static String getDefaultHomepage() {
        return "https://start.duckduckgo.com/";
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static void setIncognitoMode(@NonNull WebView webView, boolean incognito) {
        webView.getSettings().setJavaScriptEnabled(!incognito);
    }


    @SuppressLint("SetJavaScriptEnabled")
    public static void setWebSettings(@NonNull WebView webView) {


        WebSettings settings = webView.getSettings();
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + ")");


        settings.setJavaScriptEnabled(true);
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
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
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
