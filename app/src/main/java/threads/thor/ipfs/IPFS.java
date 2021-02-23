package threads.thor.ipfs;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.util.ArrayList;
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

import ipns.pb.IpnsEntryProtos;
import thor.Listener;
import thor.Loader;
import thor.LsInfoClose;
import thor.Node;
import thor.Peer;
import thor.ResolveInfo;
import threads.LogUtils;
import threads.thor.Settings;
import threads.thor.core.Content;
import threads.thor.core.blocks.BLOCKS;
import threads.thor.core.blocks.Block;

public class IPFS implements Listener {

    private static final int RESOLVE_TIMEOUT = 3000;
    private static final String PREF_KEY = "prefKey";
    private static final String PID_KEY = "pidKey";

    private static final String SWARM_PORT_KEY = "swarmPortKey";
    private static final String PUBLIC_KEY = "publicKey";
    private static final String AGENT_KEY = "agentKey";
    private static final String CONCURRENCY_KEY = "concurrencyKey";
    private static final String PRIVATE_KEY = "privateKey";
    private static final String TAG = IPFS.class.getSimpleName();
    private static IPFS INSTANCE = null;
    private final BLOCKS blocks;
    private final Node node;
    private final Object locker = new Object();

    private final LoadingCache<String, Block> cache;

    private IPFS(@NonNull Context context) throws Exception {
        this.blocks = BLOCKS.getInstance(context);


        String host = getPID(context);

        boolean init = host == null;

        node = new Node(this);

        if (init) {
            node.identity();

            setPeerID(context, node.getPeerID());
            setPublicKey(context, node.getPublicKey());
            setPrivateKey(context, node.getPrivateKey());
        } else {
            node.setPeerID(host);
            node.setPrivateKey(IPFS.getPrivateKey(context));
            node.setPublicKey(IPFS.getPublicKey(context));
        }


        node.setAgent(IPFS.getStoredAgent(context));

        node.setPort(IPFS.getSwarmPort(context));

        node.setConcurrency(getConcurrencyValue(context));
        node.setGracePeriod("10s");
        node.setHighWater(500);
        node.setLowWater(100);
        node.setResponsive(200);

        cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build(
                        new CacheLoader<String, Block>() {
                            @Nullable
                            public Block load(@NonNull String key) {
                                return blocks.getBlock(key);
                            }
                        });
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
    public static long copy(@NonNull InputStream source, @NonNull OutputStream sink) throws IOException {
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

    private static String getStoredAgent(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return sharedPref.getString(AGENT_KEY, "go-ipfs/0.8.0-dev/thor");

    }

    private static void setPrivateKey(@NonNull Context context, @NonNull String key) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PRIVATE_KEY, key);
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

    @Deprecated
    public static void resetPeerID(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PID_KEY, "");
        editor.apply();
    }

    private static void setPeerID(@NonNull Context context, @NonNull String peerID) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PID_KEY, peerID);
        editor.apply();
    }

    @Nullable
    public static String getPID(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        String pid = sharedPref.getString(PID_KEY, "");
        Objects.requireNonNull(pid);
        if (pid.isEmpty()) {
            return null;
        }
        return pid;
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

    public static int nextFreePort() {
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


    public void bootstrap() {
        if (isDaemonRunning()) {

            if (swarmPeers() < Settings.MIN_PEERS) {
                try {
                    Pair<List<String>, List<String>> result = DnsAddrResolver.getBootstrap();

                    List<String> bootstrap = result.first;
                    List<Callable<Boolean>> tasks = new ArrayList<>();
                    ExecutorService executor = Executors.newFixedThreadPool(bootstrap.size());
                    for (String address : bootstrap) {
                        tasks.add(() -> swarmConnect(address, Settings.TIMEOUT_BOOTSTRAP));
                    }

                    List<Future<Boolean>> futures = executor.invokeAll(tasks, Settings.TIMEOUT_BOOTSTRAP, TimeUnit.SECONDS);
                    for (Future<Boolean> future : futures) {
                        LogUtils.info(TAG, "\nBootstrap done " + future.isDone());
                    }


                    List<String> second = result.second;
                    tasks.clear();
                    if (!second.isEmpty()) {
                        executor = Executors.newFixedThreadPool(second.size());
                        for (String address : second) {
                            tasks.add(() -> swarmConnect(address, Settings.TIMEOUT_BOOTSTRAP));
                        }
                        futures.clear();
                        futures = executor.invokeAll(tasks, Settings.TIMEOUT_BOOTSTRAP, TimeUnit.SECONDS);
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

                            node.daemon();
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

                }
            }
        }
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

    public boolean swarmConnect(@NonNull String multiAddress, int timeout) {
        if (!isDaemonRunning()) {
            return false;
        }
        try {
            return node.swarmConnect(multiAddress, timeout);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, multiAddress + " connection failed");
        }
        return false;
    }


    public int swarmPeers() {
        if (!isDaemonRunning()) {
            return 0;
        }
        return swarm_peers();
    }


    @NonNull
    public String decodeName(@NonNull String name) {
        try {
            return node.decodeName(name);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, name + " " + throwable.getMessage());
        }
        return "";
    }

    private int swarm_peers() {

        if (isDaemonRunning()) {
            try {
                return (int) node.swarmPeers();
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        }
        return 0;
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
            AtomicLong timeout = new AtomicLong(0);
            AtomicBoolean abort = new AtomicBoolean(false);
            node.resolveName(new ResolveInfo() {
                @Override
                public boolean close() {
                    return (timeout.get() > System.currentTimeMillis()) || abort.get() || closeable.isClosed();
                }

                private void setName(@NonNull String hash, long sequence) {
                    resolvedName.set(new ResolvedName(sequence,
                            hash.replaceFirst(Content.IPFS_PATH, "")));
                }

                @Override
                public void resolved(byte[] data) {

                    try {
                        IpnsEntryProtos.IpnsEntry entry = IpnsEntryProtos.IpnsEntry.parseFrom(data);
                        Objects.requireNonNull(entry);
                        String hash = entry.getValue().toStringUtf8();
                        long seq = entry.getSequence();

                        LogUtils.error(TAG, "IpnsEntry : " + seq + " " + hash  + " " +
                                (System.currentTimeMillis() - time));

                        if (seq < last) {
                            abort.set(true);
                            return; // newest value already available
                        }
                        if(!abort.get()) {
                            if (hash.startsWith(Content.IPFS_PATH)) {
                                timeout.set(System.currentTimeMillis() + RESOLVE_TIMEOUT);
                                setName(hash, seq);
                            } else {
                                LogUtils.error(TAG, "invalid hash " + hash);
                            }
                        }
                    } catch (InvalidProtocolBufferException e) {
                        LogUtils.error(TAG, e);
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


    @Nullable
    public List<Link> links(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {

        List<Link> links = lss(cid, closeable);
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


    @NonNull
    public String resolve(@NonNull String root, @NonNull List<String> path, @NonNull Closeable closeable) throws ClosedException {

        String resultPath = Content.IPFS_PATH + root;
        for (String name : path) {
            resultPath = resultPath.concat("/").concat(name);
        }

        return resolve(resultPath, closeable);

    }

    @NonNull
    public String resolve(@NonNull String path, @NonNull Closeable closeable) throws ClosedException {
        if (!isDaemonRunning()) {
            return "";
        }
        String result = "";

        try {
            result = node.resolve(path, closeable::isClosed);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        if (closeable.isClosed()) {
            throw new ClosedException();
        }
        return result;
    }

    public boolean resolve(@NonNull String cid, @NonNull String name, @NonNull Closeable closeable) throws ClosedException {
        String res = resolve("/" + Content.IPFS + "/" + cid + "/" + name, closeable);
        return !res.isEmpty();
    }

    public boolean isDir(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {

        if (!isDaemonRunning()) {
            return false;
        }
        boolean result;
        try {
            result = node.isDir(cid, closeable::isClosed);

        } catch (Throwable e) {
            result = false;
        }
        if (closeable.isClosed()) {
            throw new ClosedException();
        }
        return result;
    }


    public long getSize(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {
        List<LinkSize> links = ls(cid, closeable);
        int size = -1;
        if (links != null) {
            for (LinkSize info : links) {
                size += info.getSize();
            }
        }
        return size;
    }


    @Nullable
    private List<Link> lss(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {
        if (!isDaemonRunning()) {
            return Collections.emptyList();
        }
        List<Link> infoList = new ArrayList<>();
        try {

            node.ls(cid, new LsInfoClose() {
                @Override
                public boolean close() {
                    return closeable.isClosed();
                }

                @Override
                public void lsInfo(String name, String hash, long size, int type) {
                    Link info = Link.create(name, hash);
                    infoList.add(info);
                }

            }, false);

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
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
    private List<LinkSize> ls(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {
        if (!isDaemonRunning()) {
            return Collections.emptyList();
        }
        List<LinkSize> infoList = new ArrayList<>();
        try {

            node.ls(cid, new LsInfoClose() {
                @Override
                public boolean close() {
                    return closeable.isClosed();
                }

                @Override
                public void lsInfo(String name, String hash, long size, int type) {
                    LinkSize info = LinkSize.create(size);
                    infoList.add(info);
                }

            }, true);

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

    @NonNull
    private Loader getLoader(@NonNull String cid, @NonNull Closeable closeable) throws Exception {
        return node.getLoader(cid, closeable::isClosed);

    }

    public void shutdown() {
        try {
            node.setShutdown(true);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    @NonNull
    public InputStream getLoaderStream(@NonNull String cid, @NonNull Closeable closeable) throws Exception {

        Loader loader = getLoader(cid, closeable);

        return new CloseableInputStream(loader, closeable);

    }

    @NonNull
    public InputStream getLoaderStream(@NonNull String cid, @NonNull Progress progress) throws Exception {

        Loader loader = getLoader(cid, progress);

        return new LoaderInputStream(loader, progress);

    }


    @Override
    public void error(String message) {
        if (message != null && !message.isEmpty()) {
            LogUtils.error(TAG, message);
        }
    }

    @Override
    public void info(String message) {
        if (message != null && !message.isEmpty()) {
            LogUtils.info(TAG, "" + message);
        }
    }

    @Override
    public void blockDelete(String key) {
        blocks.deleteBlock(key);
    }

    @Override
    public byte[] blockGet(String key) {
        //LogUtils.error(TAG, "get "  +key);
        try {
            return cache.get(key).getData();
        } catch (Throwable ignore) {
            // ignore is expected, when block is null
        }
        return null;
    }

    @Override
    public boolean blockHas(String key) {
        //LogUtils.error(TAG, "has "  +key);
        return blocks.hasBlock(key);
    }

    @Override
    public void blockPut(String key, byte[] bytes) {
        //LogUtils.error(TAG, "put " + key);
        blocks.insertBlock(key, bytes);
    }

    @Override
    public long blockSize(String key) {
        long size = -1;
        try {
            size = cache.get(key).getSize();
        } catch (Throwable ignore) {
            // ignore is expected, when block is null
        }
        // LogUtils.error(TAG, "size " + size + " " +key);
        return size;
    }

    @Override
    public void verbose(String s) {
        LogUtils.verbose(TAG, "" + s);
    }

    public boolean isDaemonRunning() {
        return node.getRunning();
    }

    public boolean isValidCID(@NonNull String cid) {
        try {
            this.node.cidCheck(cid);
            return true;
        } catch (Throwable e) {
            return false;
        }
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

    private static class LoaderInputStream extends InputStream implements AutoCloseable {
        private final Loader mLoader;
        private final Progress mProgress;
        private final long size;
        private int position = 0;
        private byte[] data = null;
        private int remember = 0;
        private long totalRead = 0L;


        LoaderInputStream(@NonNull Loader loader, @NonNull Progress progress) {
            mLoader = loader;
            mProgress = progress;
            size = mLoader.getSize();
        }

        @Override
        public int available() {
            long size = mLoader.getSize();
            return (int) size;
        }

        @Override
        public int read() throws IOException {

            try {
                if (data == null) {
                    invalidate();
                    preLoad();
                }
                if (data == null) {
                    return -1;
                }
                if (position < data.length) {
                    byte value = data[position];
                    position++;
                    return (value & 0xff);
                } else {
                    invalidate();
                    if (preLoad()) {
                        byte value = data[position];
                        position++;
                        return (value & 0xff);
                    } else {
                        return -1;
                    }
                }

            } catch (Throwable e) {
                throw new IOException(e);
            }
        }

        private void invalidate() {
            position = 0;
            data = null;
        }


        private boolean preLoad() throws Exception {

            mLoader.load(4096, mProgress::isClosed);
            int read = (int) mLoader.getRead();
            if (read > 0) {
                data = new byte[read];
                byte[] values = mLoader.getData();
                System.arraycopy(values, 0, data, 0, read);

                totalRead += read;
                if (mProgress.doProgress()) {
                    if (size > 0) {
                        int percent = (int) ((totalRead * 100.0f) / size);
                        if (remember < percent) {
                            remember = percent;
                            mProgress.setProgress(percent);
                        }
                    }
                }
                return true;
            }
            return false;
        }

        public void close() {
            try {
                mLoader.close();
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        }
    }

    private static class CloseableInputStream extends InputStream implements AutoCloseable {
        private final Loader loader;
        private final Closeable closeable;
        private int position = 0;
        private byte[] data = null;

        CloseableInputStream(@NonNull Loader loader, @NonNull Closeable closeable) {
            this.loader = loader;
            this.closeable = closeable;
        }

        @Override
        public int available() {
            long size = loader.getSize();
            return (int) size;
        }

        @Override
        public int read() throws IOException {


            try {
                if (data == null) {
                    invalidate();
                    preLoad();
                }
                if (data == null) {
                    return -1;
                }
                if (position < data.length) {
                    byte value = data[position];
                    position++;
                    return (value & 0xff);
                } else {
                    invalidate();
                    if (preLoad()) {
                        byte value = data[position];
                        position++;
                        return (value & 0xff);
                    } else {
                        return -1;
                    }
                }

            } catch (Throwable e) {
                throw new IOException(e);
            }
        }


        private void invalidate() {
            position = 0;
            data = null;
        }


        private boolean preLoad() throws Exception {
            loader.load(4096, closeable::isClosed);
            int read = (int) loader.getRead();
            if (read > 0) {
                data = new byte[read];
                byte[] values = loader.getData();
                System.arraycopy(values, 0, data, 0, read);
                return true;
            }
            return false;
        }

        public void close() {
            try {
                loader.close();
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        }
    }


}
