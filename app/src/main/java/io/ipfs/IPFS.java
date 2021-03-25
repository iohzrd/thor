package io.ipfs;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.Closeable;
import io.LogUtils;
import io.ipfs.bitswap.BitSwap;
import io.ipfs.bitswap.BitSwapNetwork;
import io.ipfs.bitswap.LiteHost;
import io.ipfs.cid.Cid;
import io.ipfs.exchange.Interface;
import io.ipfs.format.BlockStore;
import io.ipfs.utils.Link;
import io.ipfs.utils.LinkCloseable;
import io.ipfs.utils.Progress;
import io.ipfs.utils.ProgressStream;
import io.ipfs.utils.Reachable;
import io.ipfs.utils.ReaderProgress;
import io.ipfs.utils.ReaderStream;
import io.ipfs.utils.Resolver;
import io.ipfs.utils.Stream;
import io.libp2p.host.Host;
import io.libp2p.network.StreamHandler;
import io.libp2p.peer.PeerID;
import io.libp2p.protocol.Protocol;
import io.libp2p.routing.ContentRouting;
import io.protos.ipns.IpnsProtos;
import lite.Listener;
import lite.Node;
import lite.Peer;
import lite.PeerInfo;
import lite.PeerStream;
import lite.Providers;
import lite.ResolveInfo;
import threads.thor.core.blocks.BLOCKS;

public class IPFS implements Listener, ContentRouting {

    public static final int PRELOAD = 20;
    public static final int PRELOAD_DIST = 5;
    public static final int WRITE_TIMEOUT = 30;
    public static final String AGENT = "/go-ipfs/0.9.0-dev/thor";
    public static final int TIMEOUT_BOOTSTRAP = 5;
    public static final int LOW_WATER = 50;
    public static final int HIGH_WATER = 150;
    public static final String GRACE_PERIOD = "10s";
    public static final int MIN_PEERS = 10;
    public static final long RESOLVE_MAX_TIME = 20000; // 20 sec
    public static final int RESOLVE_TIMEOUT = 3000; // 3 sec

    public static final int CHUNK_SIZE = 262144;


    // BlockSizeLimit specifies the maximum size an imported block can have.
    public static final int BLOCK_SIZE_LIMIT = 1048576; // 1 MB
    public static final String IPFS_PATH = "/ipfs/";
    public static final String IPNS_PATH = "/ipns/";
    public static final String P2P_PATH = "/p2p/";
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
    // rough estimates on expected sizes
    private static final int roughLinkBlockSize = 1 << 13; // 8KB
    private static final int roughLinkSize = 34 + 8 + 5;// sha256 multihash + size + no name + protobuf framing
    // DefaultLinksPerBlock governs how the importer decides how many links there
// will be per block. This calculation is based on expected distributions of:
//  * the expected distribution of block sizes
//  * the expected distribution of link sizes
//  * desired access speed
// For now, we use:
//
//   var roughLinkBlockSize = 1 << 13 // 8KB
//   var roughLinkSize = 34 + 8 + 5   // sha256 multihash + size + no name
//                                    // + protobuf framing
//   var DefaultLinksPerBlock = (roughLinkBlockSize / roughLinkSize)
//                            = ( 8192 / 47 )
//                            = (approximately) 174
    public static final int LINKS_PER_BLOCK = roughLinkBlockSize / roughLinkSize;


    private static final String PREF_KEY = "prefKey";
    private static final String PID_KEY = "pidKey";
    private static final String PRIVATE_NETWORK_KEY = "privateNetworkKey";
    private static final String PRIVATE_SHARING_KEY = "privateSharingKey";
    private static final String SWARM_KEY = "swarmKey";
    private static final String SWARM_PORT_KEY = "swarmPortKey";
    private static final String PUBLIC_KEY = "publicKey";
    private static final String PRIVATE_KEY = "privateKey";
    private static final String CONCURRENCY_KEY = "concurrencyKey";
    private static final String TAG = IPFS.class.getSimpleName();
    private static final ExecutorService READER = Executors.newFixedThreadPool(4);
    private static IPFS INSTANCE = null;
    private final Node node;
    private final Object locker = new Object();
    private final BLOCKS blocks;
    private Pusher pusher;
    private Interface exchange;
    @NonNull
    private Reachable reachable = Reachable.UNKNOWN;
    private StreamHandler handler;


    private IPFS(@NonNull Context context) throws Exception {

        blocks = BLOCKS.getInstance(context);


        String peerID = getPeerID(context);

        boolean init = peerID == null;

        node = new Node(this);

        if (init) {
            node.identity();

            setPeerID(context, node.getPeerID());
            setPublicKey(context, node.getPublicKey());
            setPrivateKey(context, node.getPrivateKey());
        } else {
            node.setPeerID(peerID);
            node.setPrivateKey(IPFS.getPrivateKey(context));
            node.setPublicKey(IPFS.getPublicKey(context));
        }

        String swarmKey = getSwarmKey(context);
        if (!swarmKey.isEmpty()) {
            node.setSwarmKey(swarmKey.getBytes());
            node.setEnablePrivateNetwork(isPrivateNetworkEnabled(context));
        }

        node.setAgent(AGENT);
        node.setPushing(false);
        node.setPort(IPFS.getSwarmPort(context));

        node.setConcurrency(getConcurrencyValue(context));
        node.setGracePeriod(GRACE_PERIOD);
        node.setHighWater(HIGH_WATER);
        node.setLowWater(LOW_WATER);
        node.setResponsive(200);
        node.setEnablePushService(false);
        node.setEnableReachService(false);
        node.setEnableConnService(false);

    }

    public static int getConcurrencyValue(@NonNull Context context) {
        Objects.requireNonNull(context);
        SharedPreferences sharedPref = context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
        return sharedPref.getInt(CONCURRENCY_KEY, 25);
    }

    public static void setConcurrencyValue(@NonNull Context context, int timeout) {
        Objects.requireNonNull(context);
        SharedPreferences sharedPref = context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(CONCURRENCY_KEY, timeout);
        editor.apply();
    }

    public static int getSwarmPort(@NonNull Context context) {

        SharedPreferences sharedPref = context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
        return sharedPref.getInt(SWARM_PORT_KEY, 5001);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static long copy(InputStream source, OutputStream sink) throws IOException {
        long nread = 0L;
        byte[] buf = new byte[4096];
        int n;
        while ((n = source.read(buf)) > 0) {
            sink.write(buf, 0, n);
            nread += n;
        }
        return nread;
    }

    public static void copy(@NonNull InputStream source, @NonNull OutputStream sink, @NonNull ReaderProgress progress) throws IOException {
        long nread = 0L;
        byte[] buf = new byte[4096];
        int remember = 0;
        int n;
        while ((n = source.read(buf)) > 0) {
            sink.write(buf, 0, n);
            nread += n;

            if (progress.doProgress()) {
                if (progress.getSize() > 0) {
                    int percent = (int) ((nread * 100.0f) / progress.getSize());
                    if (remember < percent) {
                        remember = percent;
                        progress.setProgress(percent);
                    }
                }
            }
        }
    }

    private static void setPublicKey(@NonNull Context context, @NonNull String key) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PUBLIC_KEY, key);
        editor.apply();
    }

    private static void setPrivateKey(@NonNull Context context, @NonNull String key) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PRIVATE_KEY, key);
        editor.apply();
    }

    @NonNull
    public static String getSwarmKey(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return Objects.requireNonNull(sharedPref.getString(SWARM_KEY, ""));

    }

    public static void setSwarmKey(@NonNull Context context, @NonNull String key) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(SWARM_KEY, key);
        editor.apply();
    }

    @NonNull
    private static String getPublicKey(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return Objects.requireNonNull(sharedPref.getString(PUBLIC_KEY, ""));

    }

    @NonNull
    private static String getPrivateKey(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return Objects.requireNonNull(sharedPref.getString(PRIVATE_KEY, ""));

    }

    private static void setPeerID(@NonNull Context context, @NonNull String peerID) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PID_KEY, peerID);
        editor.apply();
    }

    @Nullable
    public static String getPeerID(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return sharedPref.getString(PID_KEY, null);
    }

    public static void setPrivateNetworkEnabled(@NonNull Context context, boolean privateNetwork) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(PRIVATE_NETWORK_KEY, privateNetwork);
        editor.apply();
    }

    public static void setPrivateSharingEnabled(@NonNull Context context, boolean privateSharing) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(PRIVATE_SHARING_KEY, privateSharing);
        editor.apply();
    }

    public static boolean isPrivateNetworkEnabled(@NonNull Context context) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return sharedPref.getBoolean(PRIVATE_NETWORK_KEY, false);
    }

    public static boolean isPrivateSharingEnabled(@NonNull Context context) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return sharedPref.getBoolean(PRIVATE_SHARING_KEY, false);
    }

    @NonNull
    public static IPFS getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (IPFS.class) {
                if (INSTANCE == null) {
                    try {
                        INSTANCE = new IPFS(context);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return INSTANCE;
    }

    private static int nextFreePort() {
        int port = ThreadLocalRandom.current().nextInt(4001, 65535);
        while (true) {
            if (isLocalPortFree(port)) {
                return port;
            } else {
                port = ThreadLocalRandom.current().nextInt(4001, 65535);
            }
        }
    }

    private static boolean isLocalPortFree(int port) {
        try {
            new ServerSocket(port).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void setPusher(@Nullable Pusher pusher) {
        node.setPushing(pusher != null);
        this.pusher = pusher;
    }

    public boolean notify(@NonNull String pid, @NonNull String cid) {
        if (!isDaemonRunning()) {
            return false;
        }
        try {
            synchronized (pid.intern()) {
                return node.push(pid, cid.getBytes()) == cid.length();
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return false;
    }

    @NonNull
    public String decodeName(@NonNull String name) {
        try {
            return node.decodeName(name);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return "";
    }

    @Override
    public void push(String cid, String pid) {
        try {
            // CID and PID are both valid objects (code done in go)
            Objects.requireNonNull(cid);
            Objects.requireNonNull(pid);
            if (pusher != null) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.submit(() -> pusher.push(pid, cid));
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }

    @NonNull
    public String getPeerID() {
        return node.getPeerID();
    }

    @NonNull
    public String getHost() {
        return base32(node.getPeerID());
    }

    public void shutdown() {
        try {
            setPusher(null);
            node.setShutdown(true);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    private void setStreamHandler(@NonNull StreamHandler streamHandler) {
        this.handler = streamHandler;
    }

    @NonNull
    public Reachable getReachable() {
        return reachable;
    }

    private void setReachable(@NonNull Reachable reachable) {
        this.reachable = reachable;
        // not active
    }

    public void bootstrap() {
        if (isDaemonRunning()) {
            if (numSwarmPeers() < MIN_PEERS) {
                try {
                    Pair<List<String>, List<String>> result = DnsAddrResolver.getBootstrap();

                    List<String> bootstrap = result.first;
                    List<Callable<Boolean>> tasks = new ArrayList<>();
                    ExecutorService executor = Executors.newFixedThreadPool(bootstrap.size());
                    for (String address : bootstrap) {
                        tasks.add(() -> swarmConnect(address, TIMEOUT_BOOTSTRAP));
                    }

                    List<Future<Boolean>> futures = executor.invokeAll(tasks, TIMEOUT_BOOTSTRAP, TimeUnit.SECONDS);
                    for (Future<Boolean> future : futures) {
                        LogUtils.info(TAG, "\nBootstrap done " + future.isDone());
                    }

                    List<String> second = result.second;
                    tasks.clear();
                    if (!second.isEmpty()) {
                        executor = Executors.newFixedThreadPool(second.size());
                        for (String address : second) {
                            tasks.add(() -> swarmConnect(address, TIMEOUT_BOOTSTRAP));
                        }
                        futures.clear();
                        futures = executor.invokeAll(tasks, TIMEOUT_BOOTSTRAP, TimeUnit.SECONDS);
                        for (Future<Boolean> future : futures) {
                            LogUtils.info(TAG, "\nConnect done " + future.isDone());
                        }
                    }
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            }
        }
    }


    public void dhtFindProviders(@NonNull String cid, int numProviders,
                                 @NonNull io.libp2p.routing.Providers providers) throws ClosedException {
        if (!isDaemonRunning()) {
            return;
        }

        if (numProviders < 1) {
            throw new RuntimeException("number of providers must be greater than 0");
        }

        try {
            node.dhtFindProviders(cid, numProviders, new Providers() {
                @Override
                public boolean close() {
                    return providers.isClosed();
                }

                @Override
                public void peer(@NonNull String id) {
                    providers.Peer(id);
                }
            });
        } catch (Throwable ignore) {
        }
        if (providers.isClosed()) {
            throw new ClosedException();
        }
    }


    public void dhtPublish(@NonNull Closeable closable, @NonNull String cid) throws ClosedException {

        if (!isDaemonRunning()) {
            return;
        }

        try {
            node.dhtProvide(cid, closable::isClosed);
        } catch (Throwable ignore) {
        }
        if (closable.isClosed()) {
            throw new ClosedException();
        }
    }

    @Nullable
    public PeerInfo pidInfo(@NonNull String pid) {

        if (!isDaemonRunning()) {
            return null;
        }
        try {
            return node.pidInfo(pid);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        return null;

    }

    @Nullable
    public PeerInfo id() {
        try {
            return node.id();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public boolean swarmConnect(@NonNull String multiAddress,
                                @NonNull Closeable closeable) throws ClosedException {
        if (!isDaemonRunning()) {
            return false;
        }
        try {
            return node.swarmConnect(multiAddress, true, closeable::isClosed);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, multiAddress + " connection failed");
        }
        if (closeable.isClosed()) {
            throw new ClosedException();
        }
        return false;
    }

    public boolean swarmConnect(@NonNull String multiAddress, int timeout) {
        if (!isDaemonRunning()) {
            return false;
        }
        try {
            return node.swarmConnectTimeout(multiAddress, timeout, true);
        } catch (Throwable e) {
            LogUtils.error(TAG, multiAddress + " " + e.getLocalizedMessage());
        }
        return false;
    }

    public boolean isPrivateNetwork() {
        return node.getPrivateNetwork();
    }

    public boolean isConnected(@NonNull String pid) {

        if (!isDaemonRunning()) {
            return false;
        }
        try {
            return node.isConnected(pid);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return false;
    }

    @Nullable
    public Peer swarmPeer(@NonNull String pid) {
        if (!isDaemonRunning()) {
            return null;
        }
        try {
            return node.swarmPeer(pid);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }

    @NonNull
    public List<String> swarmPeers() {
        if (!isDaemonRunning()) {
            return Collections.emptyList();
        }
        return swarm_peers();
    }

    @NonNull
    private List<String> swarm_peers() {

        List<String> peers = new ArrayList<>();
        if (isDaemonRunning()) {
            try {
                node.swarmPeers(peers::add);
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        }
        return peers;
    }

    public void publishName(@NonNull String cid, @NonNull Closeable closeable, int sequence)
            throws ClosedException {
        if (!isDaemonRunning()) {
            return;
        }
        try {
            node.publishName(cid, closeable::isClosed, sequence);
        } catch (Throwable ignore) {
        }

        if (closeable.isClosed()) {
            throw new ClosedException();
        }
    }

    @NonNull
    public String base32(@NonNull String pid) {
        try {
            return node.base32(pid);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }


    public void swarmDisconnect(@NonNull String pid) {
        if (!isDaemonRunning()) {
            return;
        }
        try {
            node.swarmDisconnect(P2P_PATH + pid);
        } catch (Throwable e) {
            LogUtils.error(TAG, "" + e.getLocalizedMessage());
        }
    }

    @NonNull
    public String base58(@NonNull String pid) {
        try {
            return node.base58(pid);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }


    @Nullable
    public io.ipfs.format.Node resolveNode(@NonNull String root, @NonNull List<String> path, @NonNull Closeable closeable) throws ClosedException {

        String resultPath = IPFS_PATH + root;
        for (String name : path) {
            resultPath = resultPath.concat("/").concat(name);
        }

        return resolveNode(resultPath, closeable);

    }

    @Nullable
    public io.ipfs.format.Node resolveNode(@NonNull String path, @NonNull Closeable closeable) throws ClosedException {

        try {
            return Resolver.resolveNode(closeable, blocks, exchange, path);
        } catch (Throwable ignore) {
            // common use case not resolve a a path
        }
        if (closeable.isClosed()) {
            throw new ClosedException();
        }
        return null;
    }


    @Nullable
    public ResolvedName resolveName(@NonNull String name, long last,
                                    @NonNull Closeable closeable) throws ClosedException {
        if (!isDaemonRunning()) {
            return null;
        }


        long time = System.currentTimeMillis();

        AtomicReference<ResolvedName> resolvedName = new AtomicReference<>(null);
        try {
            AtomicLong timeout = new AtomicLong(System.currentTimeMillis() + RESOLVE_MAX_TIME);
            AtomicBoolean abort = new AtomicBoolean(false);
            node.resolveName(new ResolveInfo() {
                @Override
                public boolean close() {
                    return (timeout.get() < System.currentTimeMillis()) || abort.get() || closeable.isClosed();
                }

                private void setName(@NonNull String hash, long sequence) {
                    resolvedName.set(new ResolvedName(sequence,
                            hash.replaceFirst(IPFS_PATH, "")));
                }

                @Override
                public void resolved(byte[] data) {

                    try {
                        IpnsProtos.IpnsEntry entry = IpnsProtos.IpnsEntry.parseFrom(data);
                        Objects.requireNonNull(entry);
                        String hash = entry.getValue().toStringUtf8();
                        long seq = entry.getSequence();

                        LogUtils.error(TAG, "IpnsEntry : " + seq + " " + hash + " " +
                                (System.currentTimeMillis() - time));

                        if (seq < last) {
                            abort.set(true);
                            return; // newest value already available
                        }
                        if (!abort.get()) {
                            if (hash.startsWith(IPFS_PATH)) {
                                timeout.set(System.currentTimeMillis() + RESOLVE_TIMEOUT);
                                setName(hash, seq);
                            } else {
                                LogUtils.error(TAG, "invalid hash " + hash);
                            }
                        }
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }

                }
            }, name, false, 8);

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        LogUtils.error(TAG, "Finished resolve name " + name + " " +
                (System.currentTimeMillis() - time));

        if (closeable.isClosed()) {
            throw new ClosedException();
        }
        return resolvedName.get();
    }


    public void rm(@NonNull String cid, boolean recursively) {
        try {
            Stream.Rm(() -> false, blocks, cid, recursively);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    public long getSwarmPort() {
        return node.getPort();
    }

    @Nullable
    public String storeData(@NonNull byte[] data) {

        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            return storeInputStream(inputStream);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }

    @Nullable
    public String storeText(@NonNull String content) {

        try (InputStream inputStream = new ByteArrayInputStream(content.getBytes())) {
            return storeInputStream(inputStream);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }

    @Nullable
    public String rmLinkFromDir(String dir, String name) {
        try {
            return Stream.RemoveLinkFromDir(blocks, () -> false, dir, name);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return null;
    }

    @Nullable
    public String addLinkToDir(@NonNull String dir, @NonNull String name, @NonNull String link) {
        try {
            return Stream.AddLinkToDir(blocks, () -> false, dir, name, link);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }

    @Nullable
    public String createEmptyDir() {
        try {
            return Stream.CreateEmptyDir(blocks);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }


    @NonNull
    public String resolve(@NonNull String root, @NonNull List<String> path, @NonNull Closeable closeable) throws ClosedException {

        String resultPath = IPFS_PATH + root;
        for (String name : path) {
            resultPath = resultPath.concat("/").concat(name);
        }

        return resolve(resultPath, closeable);

    }

    public String resolve(@NonNull String path, @NonNull Closeable closeable) throws ClosedException {
        if (!isDaemonRunning()) {
            return "";
        }
        String result = "";

        try {
            result = Resolver.resolve(closeable, blocks, exchange, path);
        } catch (Throwable ignore) {
            // common use case not resolve a a path
        }
        if (closeable.isClosed()) {
            throw new ClosedException();
        }
        return result;
    }

    public boolean resolve(@NonNull String cid, @NonNull String name,
                           @NonNull Closeable closeable) throws ClosedException {
        String res = resolve(IPFS_PATH + cid + "/" + name, closeable);
        return !res.isEmpty();
    }


    public boolean isDir(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {

        if (!isDaemonRunning()) {
            return false;
        }
        boolean result;
        try {
            BlockStore blockstore = BlockStore.NewBlockstore(blocks);
            result = Stream.IsDir(closeable, blockstore, exchange, cid);

        } catch (Throwable e) {
            result = false;
        }
        if (closeable.isClosed()) {
            throw new ClosedException();
        }
        return result;
    }


    public long getSize(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {
        List<Link> links = ls(cid, closeable, true);
        int size = -1;
        if (links != null) {
            for (Link info : links) {
                size += info.getSize();
            }
        }
        return size;
    }


    @Nullable
    public List<Link> links(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {

        List<Link> links = ls(cid, closeable, false);
        if (links == null) {
            LogUtils.info(TAG, "no links");
            return null;
        }

        List<Link> result = new ArrayList<>();
        for (Link link : links) {
            if (!link.getName().isEmpty()) {
                result.add(link);
            }
        }
        return result;
    }

    @Nullable
    public List<Link> getLinks(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {

        List<Link> links = ls(cid, closeable, true);
        if (links == null) {
            LogUtils.info(TAG, "no links");
            return null;
        }

        List<Link> result = new ArrayList<>();
        for (Link link : links) {

            if (!link.getName().isEmpty()) {
                result.add(link);
            }
        }
        return result;
    }


    @Nullable
    public List<Link> ls(@NonNull String cid, @NonNull Closeable closeable,
                         boolean resolveChildren) throws ClosedException {
        if (!isDaemonRunning()) {
            return Collections.emptyList();
        }
        List<Link> infoList = new ArrayList<>();
        try {
            BlockStore blockstore = BlockStore.NewBlockstore(blocks);
            Stream.Ls(new LinkCloseable() {

                @Override
                public boolean isClosed() {
                    return closeable.isClosed();
                }

                @Override
                public void info(@NonNull Link link) {
                    infoList.add(link);
                }
            }, blockstore, exchange, cid, resolveChildren);

        } catch (Throwable e) {
            if (closeable.isClosed()) {
                throw new ClosedException();
            }
            return null;
        }
        if (closeable.isClosed()) {
            throw new ClosedException();
        }
        return infoList;
    }

    @Nullable
    public String storeFile(@NonNull File target) {
        try (FileInputStream inputStream = new FileInputStream(target)) {
            return storeInputStream(inputStream);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return null;
    }

    @NonNull
    public io.ipfs.utils.Reader getReader(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {
        BlockStore blockstore = BlockStore.NewBlockstore(blocks);
        return io.ipfs.utils.Reader.getReader(closeable, blockstore, exchange, cid);
    }

    private void getToOutputStream(@NonNull OutputStream outputStream, @NonNull String cid,
                                   @NonNull Closeable closeable) throws Exception {
        try (InputStream inputStream = getInputStream(cid, closeable)) {
            IPFS.copy(inputStream, outputStream);
        }
    }

    public void storeToFile(@NonNull File file, @NonNull String cid, @NonNull Progress progress) {
        if (!isDaemonRunning()) {
            return;
        }
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            storeToOutputStream(outputStream, progress, cid);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public void storeToOutputStream(@NonNull OutputStream os, @NonNull Progress progress,
                                    @NonNull String cid) throws Exception {

        long totalRead = 0L;
        int remember = 0;

        io.ipfs.utils.Reader reader = getReader(cid, progress);
        long size = reader.getSize();
        byte[] buf = reader.loadNextData();
        while (buf != null && buf.length > 0) {

            if (progress.isClosed()) {
                throw new RuntimeException("Progress closed");
            }

            // calculate progress
            totalRead += buf.length;
            if (progress.doProgress()) {
                if (size > 0) {
                    int percent = (int) ((totalRead * 100.0f) / size);
                    if (remember < percent) {
                        remember = percent;
                        progress.setProgress(percent);
                    }
                }
            }

            os.write(buf, 0, buf.length);

            buf = reader.loadNextData();

        }


    }

    public void storeToOutputStream(@NonNull OutputStream os, @NonNull String cid, @NonNull Closeable closeable) throws Exception {


        io.ipfs.utils.Reader reader = getReader(cid, closeable);
        byte[] buf = reader.loadNextData();
        while (buf != null && buf.length > 0) {

            os.write(buf, 0, buf.length);
            buf = reader.loadNextData();
        }


    }

    @NonNull
    public InputStream getLoaderStream(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {
        io.ipfs.utils.Reader loader = getReader(cid, closeable);
        return new ReaderStream(loader);
    }

    @NonNull
    public InputStream getLoaderStream(@NonNull String cid, @NonNull Progress progress) throws ClosedException {
        io.ipfs.utils.Reader loader = getReader(cid, progress);
        return new ProgressStream(loader, progress);

    }

    public void storeToFile(@NonNull File file, @NonNull String cid, @NonNull Closeable closeable) throws Exception {

        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            storeToOutputStream(fileOutputStream, cid, closeable);
        }
    }

    @Nullable
    public String storeInputStream(@NonNull InputStream inputStream,
                                   @NonNull Progress progress, long size) {


        String res = "";
        try {
            res = Stream.Write(blocks, new io.ipfs.utils.WriterStream(inputStream, progress, size));
        } catch (Throwable e) {
            if (!progress.isClosed()) {
                LogUtils.error(TAG, e);
            }
        }

        if (!res.isEmpty()) {
            return res;
        }
        return null;
    }

    @Nullable
    public String storeInputStream(@NonNull InputStream inputStream) {

        return storeInputStream(inputStream, new Progress() {
            @Override
            public boolean isClosed() {
                return false;
            }

            @Override
            public void setProgress(int progress) {
            }

            @Override
            public boolean doProgress() {
                return false;
            }


        }, 0);
    }

    @Nullable
    public String getText(@NonNull String cid, @NonNull Closeable closeable) {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            getToOutputStream(outputStream, cid, closeable);
            return new String(outputStream.toByteArray());
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            return null;
        }
    }

    @Nullable
    public byte[] getData(@NonNull String cid, @NonNull Closeable closeable) {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            getToOutputStream(outputStream, cid, closeable);
            return outputStream.toByteArray();
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            return null;
        }
    }

    @Nullable
    public byte[] loadData(@NonNull String cid, @NonNull Progress progress) throws Exception {
        if (!isDaemonRunning()) {
            return null;
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            storeToOutputStream(outputStream, progress, cid);
            return outputStream.toByteArray();
        }
    }

    public void startDaemon() {
        if (!node.getRunning()) {
            synchronized (locker) {
                if (!node.getRunning()) {
                    AtomicBoolean failure = new AtomicBoolean(false);
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    AtomicReference<String> exception = new AtomicReference<>("");
                    executor.submit(() -> {
                        try {

                            long port = node.getPort();
                            if (!isLocalPortFree((int) port)) {
                                node.setPort(nextFreePort());
                            }
                            LogUtils.error(TAG, "start daemon...");
                            node.daemon();
                            LogUtils.error(TAG, "stop daemon...");
                        } catch (Throwable e) {
                            failure.set(true);
                            exception.set("" + e.getLocalizedMessage());
                            LogUtils.error(TAG, e);
                        }
                    });

                    while (!node.getRunning()) {
                        if (failure.get()) {
                            break;
                        }
                    }
                    if (failure.get()) {
                        throw new RuntimeException(exception.get());
                    }


                    if (node.getRunning()) {
                        List<Protocol> protocols = new ArrayList<>();

                        ContentRouting contentRouting = this;
                        protocols.add(Protocol.ProtocolBitswap);


                        BitSwapNetwork bsm = LiteHost.NewLiteHost(new Host() {
                            @Override
                            public List<PeerID> getPeers() {
                                List<PeerID> peers = new ArrayList<>();
                                if (isDaemonRunning()) {
                                    try {
                                        node.swarmPeers(ID -> peers.add(new PeerID(ID)));
                                    } catch (Throwable e) {
                                        LogUtils.error(TAG, e);
                                    }
                                }
                                return peers;
                            }

                            @Override
                            public boolean Connect(@NonNull Closeable closeable,
                                                   @NonNull PeerID peer, boolean protect) throws ClosedException {
                                try {
                                    return node.swarmConnect(P2P_PATH + peer.String(),
                                            protect, closeable::isClosed);
                                } catch (Throwable throwable) {
                                    if (closeable.isClosed()) {
                                        throw new ClosedException();
                                    }
                                    return false;
                                }
                            }

                            @Override
                            public long WriteMessage(@NonNull Closeable closeable,
                                                     @NonNull PeerID peer,
                                                     @NonNull List<Protocol> protocols,
                                                     @NonNull byte[] bytes,
                                                     int timeout)
                                    throws ClosedException, ProtocolNotSupported {

                                try {
                                    String protos = "";
                                    for (int i = 0; i < protocols.size(); i++) {
                                        if (i > 1) {
                                            protos = protos.concat(";");
                                        }
                                        protos = protos.concat(protocols.get(i).String());
                                    }
                                    return node.writeMessage(closeable::isClosed, peer.String(),
                                            protos, bytes, timeout);
                                } catch (Throwable throwable) {
                                    if (closeable.isClosed()) {
                                        throw new ClosedException();
                                    } else {
                                        String msg = throwable.getMessage();
                                        if(Objects.equals(msg, "protocol not supported")){
                                            throw new ProtocolNotSupported();
                                        } else {
                                            throw new RuntimeException(throwable);
                                        }
                                    }
                                }
                            }


                            @Override
                            public void SetStreamHandler(@NonNull Protocol proto,
                                                         @NonNull StreamHandler handler) {
                                setStreamHandler(handler);
                                node.setStreamHandler(proto.String());

                            }

                            @Override
                            public PeerID Self() {
                                return new PeerID(getPeerID());
                            }
                        }, contentRouting, protocols);


                        BlockStore blockstore = BlockStore.NewBlockstore(blocks);

                        exchange = BitSwap.New(bsm, blockstore);
                    }
                }
            }
        }
    }

    @Override
    public boolean allowConnect(String peer) {
        // everybody can push to you
        return true;
    }


    @Override
    public void bitSwapData(String pid, String proto, byte[] bytes) {
        LogUtils.verbose(TAG, "Receive message from " + pid + " proto " + proto + " data " + bytes.length);
        Objects.requireNonNull(handler);

        READER.execute(() -> handler.message(new PeerID(pid), Protocol.create(proto), bytes));
    }

    @Override
    public void bitSwapError(String pid, String proto, String error) {
        LogUtils.error(TAG, "Receive error from " + pid + " proto " + proto + " error " + error);
        Objects.requireNonNull(handler);
        handler.error(new PeerID(pid), Protocol.create(proto), error);
    }

    @Override
    public boolean bitSwapGate(String pid) {
        Objects.requireNonNull(handler);
        return handler.gate(new PeerID(pid));
    }


    @Override
    public void connected(String pretty) {
        LogUtils.error(TAG, pretty);
    }

    @Override
    public void error(String message) {
        if (message != null && !message.isEmpty()) {
            LogUtils.error(TAG, "" + message);
        }
    }

    @Override
    public void info(String message) {
        if (message != null && !message.isEmpty()) {
            LogUtils.info(TAG, "" + message);
        }
    }

    @Override
    public void reachablePrivate() {
        setReachable(Reachable.PRIVATE);
    }

    @Override
    public void reachablePublic() {
        setReachable(Reachable.PUBLIC);
    }

    @Override
    public void reachableUnknown() {
        setReachable(Reachable.UNKNOWN);
    }

    @Override
    public void verbose(String s) {
        LogUtils.verbose(TAG, "" + s);
    }

    @NonNull
    public InputStream getInputStream(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {
        io.ipfs.utils.Reader reader = getReader(cid, closeable);
        return new ReaderStream(reader);

    }

    public boolean isDaemonRunning() {
        return node.getRunning();
    }

    public boolean isValidCID(@NonNull String cid) {
        try {
            return !Cid.Decode(cid).String().isEmpty();
        } catch (Throwable e) {
            return false;
        }
    }


    public long numSwarmPeers() {
        if (!isDaemonRunning()) {
            return 0;
        }
        return node.numSwarmPeers();
    }


    @Override
    public void FindProvidersAsync(@NonNull io.libp2p.routing.Providers providers, @NonNull Cid cid, int number) throws ClosedException {

        dhtFindProviders(cid.String(), number, providers);

    }

    @Override
    public void Provide(@NonNull Closeable closeable, @NonNull Cid cid) {
        try {
            dhtPublish(closeable, cid.String());
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }

    }

    public void reset() {
        try {
            if (exchange != null) {
                exchange.reset();
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void load(@NonNull Closeable closeable, @NonNull String cid) {
        try {
            if (exchange != null) {
                exchange.load(closeable, Cid.Decode(cid));
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    public interface Pusher {
        void push(@NonNull String pid, @NonNull String cid);
    }

    public static class ResolvedName {
        private final long sequence;
        @NonNull
        private final String hash;

        public ResolvedName(long sequence, @NonNull String hash) {
            this.sequence = sequence;
            this.hash = hash;
        }

        public long getSequence() {
            return sequence;
        }

        @NonNull
        public String getHash() {
            return hash;
        }
    }

}
