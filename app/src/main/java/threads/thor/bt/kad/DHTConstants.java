package threads.thor.bt.kad;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class DHTConstants {

    public static final int MAX_ENTRIES_PER_BUCKET = 8;
    public static final int MAX_ACTIVE_TASKS = 7;
    public static final int MAX_ACTIVE_CALLS = 256;
    public static final int MAX_CONCURRENT_REQUESTS = 10;
    public static final int MAX_CONCURRENT_REQUESTS_LOWPRIO = 3;
    public static final int RPC_CALL_TIMEOUT_MAX = 10 * 1000;
    public static final int RPC_CALL_TIMEOUT_BASELINE_MIN = 100; // ms
    public static final int BOOTSTRAP_IF_LESS_THAN_X_PEERS = 30;

    static final int DHT_UPDATE_INTERVAL = 1000;
    static final int BUCKET_REFRESH_INTERVAL = 15 * 60 * 1000;
    static final int RECEIVE_BUFFER_SIZE = 5 * 1024;
    static final int CHECK_FOR_EXPIRED_ENTRIES = 5 * 60 * 1000;
    static final int MAX_ITEM_AGE = 60 * 60 * 1000;
    static final int TOKEN_TIMEOUT = 5 * 60 * 1000;
    static final int MAX_DB_ENTRIES_PER_KEY = 6000;
    // enter survival mode if we don't see new packets after this time
    static final int REACHABILITY_TIMEOUT = 60 * 1000;
    static final int BOOTSTRAP_MIN_INTERVAL = 4 * 60 * 1000;
    static final int USE_BT_ROUTER_IF_LESS_THAN_X_PEERS = 10;
    static final int SELF_LOOKUP_INTERVAL = 30 * 60 * 1000;
    static final int RANDOM_LOOKUP_INTERVAL = 10 * 60 * 1000;

    static final int ANNOUNCE_CACHE_MAX_AGE = 30 * 60 * 1000;
    static final int ANNOUNCE_CACHE_FAST_LOOKUP_AGE = 8 * 60 * 1000;


    static final InetSocketAddress[] UNRESOLVED_BOOTSTRAP_NODES = new InetSocketAddress[]{
            InetSocketAddress.createUnresolved("dht.transmissionbt.com", 6881),
            InetSocketAddress.createUnresolved("router.bittorrent.com", 6881),
            InetSocketAddress.createUnresolved("router.utorrent.com", 6881),
            InetSocketAddress.createUnresolved("router.silotis.us", 6881),
    };


    public static String getVersion() {
        return "ml" + new String(new byte[]{(byte) (11 >> 8 & 0xFF), (byte) (11 & 0xff)}, StandardCharsets.ISO_8859_1);
    }

}
