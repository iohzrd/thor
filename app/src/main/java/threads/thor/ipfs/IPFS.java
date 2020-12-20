package threads.thor.ipfs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import thor.Listener;
import thor.Loader;
import thor.LsInfoClose;
import thor.Node;
import thor.ResolveInfo;
import threads.LogUtils;
import threads.thor.Settings;

public class IPFS implements Listener {
    @NonNull
    private static final List<String> DNS_ADDRS = new ArrayList<>();

    private static final String BADGERS = "badgers";
    private static final String EMPTY_DIR_58 = "QmUNLLsPACCz1vLxQVkXqqLX5R1X345qqfHbsf67hvA3Nn";
    private static final String EMPTY_DIR_32 = "bafybeiczsscdsbs7ffqz55asqdf3smv6klcw3gofszvwlyarci47bgf354";
    private static final String PREF_KEY = "prefKey";
    private static final String PID_KEY = "pidKey";

    private static final String SWARM_PORT_KEY = "swarmPortKey";
    private static final String PUBLIC_KEY = "publicKey";
    private static final String AGENT_KEY = "agentKey";
    private static final String CLEAN_KEY = "cleanKey";
    private static final String PRIVATE_KEY = "privateKey";
    private static final long TIMEOUT = 30000L;
    private static final String TAG = IPFS.class.getSimpleName();
    private static IPFS INSTANCE = null;

    private final Node node;
    private final Object locker = new Object();


    private IPFS(@NonNull Context context) throws Exception {
        File baseDir = context.getFilesDir();


        if (IPFS.getCleanFlag(context)) {
            IPFS.setCleanFlag(context, false);
            File badger = new File(baseDir, BADGERS);
            if (badger.exists()) {
                deleteFile(badger);
            }
        }


        String host = getPID(context);

        boolean init = host == null;

        node = new Node(this, baseDir.getAbsolutePath());

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


        node.setGracePeriod("10s");
        node.setHighWater(200);
        node.setLowWater(50);

        node.openDatabase();
    }

    private static CopyOnWriteArrayList<String> getBootstrap() {


        CopyOnWriteArrayList<String> bootstrap = new CopyOnWriteArrayList<>(
                Settings.IPFS_BOOTSTRAP_NODES);

        if (IPFS.DNS_ADDRS.isEmpty()) {
            IPFS.DNS_ADDRS.addAll(DnsAddrResolver.getMultiAddresses());
        }

        CopyOnWriteArrayList<String> dnsAddrs = new CopyOnWriteArrayList<>(IPFS.DNS_ADDRS);
        bootstrap.addAll(dnsAddrs);
        return bootstrap;
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

    private static boolean getCleanFlag(@NonNull Context context) {

        SharedPreferences sharedPref = context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
        return sharedPref.getBoolean(CLEAN_KEY, false);
    }

    public static void setCleanFlag(@NonNull Context context, boolean clean) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(CLEAN_KEY, clean);
        editor.apply();
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

    public static void logCacheDir(@NonNull Context context) {
        try {
            File[] files = context.getCacheDir().listFiles();
            if (files != null) {
                for (File file : files) {
                    LogUtils.error(TAG, "" + file.length() + " " + file.getAbsolutePath());
                    if (file.isDirectory()) {
                        File[] children = file.listFiles();
                        if (children != null) {
                            for (File child : children) {
                                LogUtils.error(TAG, "" + child.length() + " " + child.getAbsolutePath());
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    public static void logBaseDir(@NonNull Context context) {
        try {
            File[] files = context.getFilesDir().listFiles();
            if (files != null) {
                for (File file : files) {
                    LogUtils.warning(TAG, "" + file.length() + " " + file.getAbsolutePath());
                    if (file.isDirectory()) {
                        logDir(file);
                    }
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    public static void logDir(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                LogUtils.warning(TAG, "" + child.length() + " " + child.getAbsolutePath());
                if (child.isDirectory()) {
                    logDir(child);
                }
            }
        }
    }

    private static void deleteFile(@NonNull File root) {
        try {
            if (root.isDirectory()) {
                File[] files = root.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isDirectory()) {
                            deleteFile(file);
                            boolean result = file.delete();
                            if (!result) {
                                LogUtils.error(TAG, "File " + file.getName() + " not deleted");
                            }
                        } else {
                            boolean result = file.delete();
                            if (!result) {
                                LogUtils.error(TAG, "File " + file.getName() + " not deleted");
                            }
                        }
                    }
                }
                boolean result = root.delete();
                if (!result) {
                    LogUtils.error(TAG, "File " + root.getName() + " not deleted");
                }
            } else {
                boolean result = root.delete();
                if (!result) {
                    LogUtils.error(TAG, "File " + root.getName() + " not deleted");
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    public void bootstrap(int minPeers, int timeout) {
        checkDaemon();
        if (swarmPeers() < minPeers) {
            try {
                CopyOnWriteArrayList<String> bootstrap = IPFS.getBootstrap();
                List<Callable<Boolean>> tasks = new ArrayList<>();
                ExecutorService executor = Executors.newFixedThreadPool(bootstrap.size());
                for (String address : bootstrap) {
                    tasks.add(() -> swarmConnect(address, timeout));
                }

                List<Future<Boolean>> result = executor.invokeAll(tasks, timeout, TimeUnit.SECONDS);
                for (Future<Boolean> future : result) {
                    LogUtils.debug(TAG, "Bootstrap done " + future.isDone());
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }

    }

    private synchronized void startDaemon() {
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
                    long time = 0L;
                    while (!node.getRunning() && time <= TIMEOUT && !failure.get()) {
                        time = time + 100L;
                        try {
                            Thread.sleep(100);
                        } catch (Throwable e) {
                            throw new RuntimeException(exception.get());
                        }
                    }
                    if (failure.get()) {
                        throw new RuntimeException(exception.get());
                    }

                }
            }
        }
    }


    private void checkDaemon() {
        if (!isDaemonRunning()) {
            startDaemon();
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
        checkDaemon();
        try {
            return node.swarmConnect(multiAddress, timeout);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, multiAddress);
            LogUtils.error(TAG, throwable);
        }
        return false;
    }


    public int swarmPeers() {
        checkDaemon();
        return swarm_peers();
    }


    private int swarm_peers() {

        int counter = 0;
        if (isDaemonRunning()) {
            try {
                return (int) node.swarmPeers();
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        }
        return counter;
    }


    @Nullable
    public ResolvedName resolveName(@NonNull String name, final long sequence,
                                    @NonNull Closeable closeable) {
        if (!isDaemonRunning()) {
            return null;
        }


        long time = System.currentTimeMillis();

        AtomicReference<ResolvedName> resolvedName = new AtomicReference<>(null);
        try {
            AtomicInteger counter = new AtomicInteger(0);
            AtomicBoolean close = new AtomicBoolean(false);
            node.resolveName(new ResolveInfo() {
                @Override
                public boolean close() {
                    return close.get() || closeable.isClosed();
                }

                @Override
                public void resolved(String hash, long seq) {


                    LogUtils.error(TAG, "" + seq + " " + hash);

                    if (seq < sequence) {
                        return; // ignore old values
                    }

                    if (hash.startsWith("/ipfs/")) {
                        if (seq > sequence || counter.get() < 2) {
                            close.set(true);
                        } else {
                            counter.incrementAndGet();
                        }
                        resolvedName.set(new ResolvedName(
                                seq, hash.replaceFirst("/ipfs/", "")));
                    }

                }
            }, name, false, 8);

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        LogUtils.error(TAG, "Finished resolve name " + name + " " +
                (System.currentTimeMillis() - time));

        return resolvedName.get();
    }

    @Nullable
    public LinkInfo getLinkInfo(@NonNull String dir, @NonNull List<String> path, @NonNull Closeable closeable) {

        LinkInfo linkInfo = null;
        String root = dir;

        for (String name : path) {
            linkInfo = getLinkInfo(root, name, closeable);
            if (linkInfo != null) {
                root = linkInfo.getContent();
            } else {
                break;
            }
        }

        return linkInfo;
    }

    @Nullable
    public LinkInfo getLinkInfo(@NonNull String dir, @NonNull String name, @NonNull Closeable closeable) {
        List<LinkInfo> links = ls(dir, closeable);
        if (links != null) {
            for (LinkInfo info : links) {
                if (Objects.equals(info.getName(), name)) {
                    return info;
                }
            }
        }
        return null;
    }

    @Nullable
    public List<LinkInfo> getLinks(@NonNull String cid, @NonNull Closeable closeable) {

        LogUtils.info(TAG, "Lookup CID : " + cid);

        List<LinkInfo> links = ls(cid, closeable);
        if (links == null) {
            LogUtils.info(TAG, "no links or stopped");
            return null;
        }

        List<LinkInfo> result = new ArrayList<>();
        for (LinkInfo link : links) {
            LogUtils.info(TAG, "Link : " + link.toString());
            if (!link.getName().isEmpty()) {
                result.add(link);
            }
        }
        return result;
    }

    @Nullable
    public List<LinkInfo> ls(@NonNull String cid, @NonNull Closeable closeable) {
        checkDaemon();
        List<LinkInfo> infoList = new ArrayList<>();
        try {

            node.ls(cid, new LsInfoClose() {
                @Override
                public boolean close() {
                    return closeable.isClosed();
                }

                @Override
                public void lsInfo(String name, String hash, long size, int type) {
                    LinkInfo info = LinkInfo.create(name, hash, size, type);
                    infoList.add(info);
                }

            });

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            return null;
        }
        if (closeable.isClosed()) {
            return null;
        }
        return infoList;
    }

    @NonNull
    public Loader getLoader(@NonNull String cid, @NonNull Closeable closeable) throws Exception {
        return node.getLoader(cid, closeable::isClosed);

    }

    @NonNull
    public InputStream getLoaderStream(@NonNull String cid, @NonNull Closeable closeable, long readTimeoutMillis) throws Exception {

        Loader loader = getLoader(cid, closeable);

        return new CloseableInputStream(loader, readTimeoutMillis);

    }

    @NonNull
    public InputStream getLoaderStream(@NonNull String cid, @NonNull Progress progress) throws Exception {

        Loader loader = getLoader(cid, progress);

        return new LoaderInputStream(loader, progress);

    }

    public long getSize(@NonNull String cid, @NonNull Closeable closeable) {
        List<LinkInfo> links = ls(cid, closeable);
        int size = -1;
        if (links != null) {
            for (LinkInfo info : links) {
                size += info.getSize();
            }
        }
        return size;
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
    public void verbose(String s) {
        LogUtils.verbose(TAG, "" + s);
    }

    public boolean isDaemonRunning() {
        return node.getRunning();
    }

    public boolean isValidCID(String multihash) {
        try {
            this.node.cidCheck(multihash);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public boolean isEmptyDir(@NonNull String cid) {
        return Objects.equals(cid, EMPTY_DIR_32) || Objects.equals(cid, EMPTY_DIR_58);
    }

    public boolean isDir(@NonNull String doc, @NonNull Closeable closeable) {
        List<LinkInfo> links = getLinks(doc, closeable);
        return links != null && !links.isEmpty();
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
        private final Loader mLoader;
        private final long readTimeoutMillis;
        private int position = 0;
        private byte[] data = null;

        CloseableInputStream(@NonNull Loader loader, long readTimeoutMillis) {
            mLoader = loader;
            this.readTimeoutMillis = readTimeoutMillis;
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
            long start = System.currentTimeMillis();
            mLoader.load(4096, () -> (System.currentTimeMillis() - start) > (readTimeoutMillis));
            int read = (int) mLoader.getRead();
            if (read > 0) {
                data = new byte[read];
                byte[] values = mLoader.getData();
                System.arraycopy(values, 0, data, 0, read);
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


}
