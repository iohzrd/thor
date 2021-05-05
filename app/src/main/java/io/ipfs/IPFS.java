package io.ipfs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.primitives.Bytes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.LogUtils;
import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.core.ConnectionIssue;
import io.ipfs.core.TimeoutCloseable;
import io.ipfs.dht.Routing;
import io.ipfs.format.BlockStore;
import io.ipfs.format.Node;
import io.ipfs.host.AddrInfo;
import io.ipfs.host.Connection;
import io.ipfs.host.DnsResolver;
import io.ipfs.host.LiteHost;
import io.ipfs.host.LiteSignedCertificate;
import io.ipfs.host.PeerInfo;
import io.ipfs.multibase.Base58;
import io.ipfs.multibase.Multibase;
import io.ipfs.multihash.Multihash;
import io.ipfs.push.PushReceiver;
import io.ipfs.utils.Link;
import io.ipfs.utils.LinkCloseable;
import io.ipfs.utils.Progress;
import io.ipfs.utils.ProgressStream;
import io.ipfs.utils.Reachable;
import io.ipfs.utils.Reader;
import io.ipfs.utils.ReaderProgress;
import io.ipfs.utils.ReaderStream;
import io.ipfs.utils.Resolver;
import io.ipfs.utils.Stream;
import io.ipfs.utils.WriterStream;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multiformats.Protocol;
import io.libp2p.crypto.keys.RsaPrivateKey;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.util.SimpleTrustManagerFactory;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.util.internal.EmptyArrays;
import ipns.pb.Ipns;
import threads.thor.core.blocks.BLOCKS;

public class IPFS implements PushReceiver {
    // TimeFormatIpfs is the format ipfs uses to represent time in string form
    // RFC3339Nano = "2006-01-02T15:04:05.999999999Z07:00"
    public static final String TimeFormatIpfs = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS'Z'";

    public static final String KAD_DHT_PROTOCOL = "/ipfs/kad/1.0.0";
    public static final String PUSH_PROTOCOL = "/ipfs/push/1.0.0";
    public static final String STREAM_PROTOCOL = "/multistream/1.0.0";
    public static final String IDENTITY_PROTOCOL = "/ipfs/id/1.0.0";
    public static final String INDEX_HTML = "index.html";
    public static final int PRELOAD = 25;
    public static final int PRELOAD_DIST = 5;
    public static final String AGENT = "/go-ipfs/0.9.0/thor"; // todo rename
    public static final String PROTOCOL_VERSION = "ipfs/0.1.0";  // todo check again
    public static final int TIMEOUT_BOOTSTRAP = 20;
    public static final long TIMEOUT_PUSH = 3;
    public static final int LOW_WATER = 50;
    public static final int HIGH_WATER = 300;
    public static final int GRACE_PERIOD = 10;
    public static final int TIMEOUT_SEND = 1;
    public static final int TIMEOUT_REQUEST = 30;
    public static final int MIN_PEERS = 10;
    public static final long RESOLVE_MAX_TIME = 30000; // 30 sec
    public static final int RESOLVE_TIMEOUT = 1000; // 1 sec
    public static final long WANTS_WAIT_TIMEOUT = 2000; // 2 sec
    public static final int CHUNK_SIZE = 262144;
    public static final String BITSWAP_PROTOCOL = "/ipfs/bitswap/1.2.0";

    // BlockSizeLimit specifies the maximum size an imported block can have.
    public static final int BLOCK_SIZE_LIMIT = 1048576; // 1 MB
    public static final String IPFS_PATH = "/ipfs/";
    public static final String IPNS_PATH = "/ipns/";
    public static final String P2P_PATH = "/p2p/";

    // IPFS BOOTSTRAP
    @NonNull
    public static final List<String> IPFS_BOOTSTRAP_NODES = new ArrayList<>(Arrays.asList(
            "/ip4/147.75.195.153/tcp/4001/p2p/QmW9m57aiBDHAkKj9nmFSEn7ZqrcF1fZS4bipsTCHburei",// default relay  libp2p
            "/ip4/147.75.70.221/tcp/4001/p2p/Qme8g49gm3q4Acp7xWBKg3nAa9fxZ1YmyDJdyGgoG6LsXh",// default relay  libp2p


            "/ip4/104.131.131.82/tcp/4001/p2p/QmaCpDMGvV2BGHeYERUEnRQAwe3N8SzbUtfsmvsqQLuvuJ", // mars.i.ipfs.io

            "/dnsaddr/bootstrap.libp2p.io/p2p/QmNnooDu7bfjPFoTZYxMNLWUQJyrVwtbZg5gBMjTezGAJN", // default dht peer
            "/dnsaddr/bootstrap.libp2p.io/p2p/QmQCU2EcMqAqQPR2i9bChDtGNJchTbq5TbXJJ16u19uLTa", // default dht peer
            "/dnsaddr/bootstrap.libp2p.io/p2p/QmbLHAnMoJPWSCR5Zhtx6BHJX9KiKNN6tpvbUcqanj75Nb", // default dht peer
            "/dnsaddr/bootstrap.libp2p.io/p2p/QmcZf59bWwK5XFi76CZX8cbJ4BhTzzA3gU1ZjYZcYW3dwt" // default dht peer

    ));
    // IPFS BOOTSTRAP DNS
    public static final String LIB2P_DNS = "bootstrap.libp2p.io";
    public static final boolean SEND_DONT_HAVES = false;
    public static final boolean BITSWAP_ENGINE_ACTIVE = false;
    public static final int KAD_DHT_BUCKET_SIZE = 20;
    // The number of peers closest to a target that must have responded for a query path to terminate
    public static final int KAD_DHT_BETA = 20;
    public static final String NA = "na";

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
    private static final String SWARM_PORT_KEY = "swarmPortKey";
    private static final String PRIVATE_KEY = "privateKey";
    private static final String CONCURRENCY_KEY = "concurrencyKey";
    private static final String TAG = IPFS.class.getSimpleName();
    private static final boolean CONNECTION_SERVICE_ENABLED = false;

    private static IPFS INSTANCE = null;

    private final BLOCKS blocks;

    public static final ConcurrentHashMap<PeerId, PubKey> remotes = new ConcurrentHashMap<>();
    private final int port;

    private final LiteHost liteHost;
    private final Set<PeerId> swarm = ConcurrentHashMap.newKeySet();
    private Pusher pusher;
    private Connector connector;
    private static final TrustManager tm = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String s) {
            try {
                // TODO activate again
                if (false) {
                    for (X509Certificate cert : chain) {
                        PubKey pubKey = LiteSignedCertificate.extractPublicKey(cert);
                        Objects.requireNonNull(pubKey);
                        PeerId peerId = PeerId.fromPubKey(pubKey);
                        Objects.requireNonNull(peerId);
                        remotes.put(peerId, pubKey);
                    }
                }
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String s) {

            try {
                // TODO activate again
                if (false) {
                    for (X509Certificate cert : chain) {
                        PubKey pubKey = LiteSignedCertificate.extractPublicKey(cert);
                        Objects.requireNonNull(pubKey);
                        PeerId peerId = PeerId.fromPubKey(pubKey);
                        Objects.requireNonNull(peerId);
                        remotes.put(peerId, pubKey);
                    }
                }
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return EmptyArrays.EMPTY_X509_CERTIFICATES;
        }
    };
    @NonNull
    private Reachable reachable = Reachable.UNKNOWN;
    public static QuicSslContext SERVER_SSL_INSTANCE;
    public static QuicSslContext CLIENT_SSL_INSTANCE;
    //private final Host host;
    private final PrivKey privateKey;

    // todo invoke this function not very often, try to work with PeerId
    @NonNull
    public static PeerId decode(@NonNull String name) {

        if (name.startsWith("Qm") || name.startsWith("1")) {
            // base58 encoded sha256 or identity multihash
            return PeerId.fromBase58(name);
        }
        byte[] data = Multibase.decode(name);

        if (data[0] == 0) {
            Multihash mh = new Multihash(Multihash.Type.id, data);
            return PeerId.fromBase58(Base58.encode(mh.getHash()));
        } else {
            try (InputStream inputStream = new ByteArrayInputStream(data)) {
                long version = Multihash.readVarint(inputStream);
                if (version != 1) {
                    throw new Exception("invalid version");
                }
                long codecType = Multihash.readVarint(inputStream);
                if (!(codecType == Cid.DagProtobuf || codecType == Cid.Raw || codecType == Cid.Libp2pKey)) {
                    throw new Exception("not supported codec");
                }
                Multihash mh = Multihash.deserialize(inputStream);
                return PeerId.fromBase58(mh.toBase58());

            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }
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

    public static int getPort(@NonNull Context context) {

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

    private static void setPrivateKey(@NonNull Context context, @NonNull String key) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PRIVATE_KEY, key);
        editor.apply();
    }

    @NonNull
    private static String getPrivateKey(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return Objects.requireNonNull(sharedPref.getString(PRIVATE_KEY, ""));

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

    private IPFS(@NonNull Context context) throws Exception {


        blocks = BLOCKS.getInstance(context);


        String algorithm = "RSA";
        final KeyPair keypair;

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithm);
        keyGen.initialize(2048, LiteSignedCertificate.ThreadLocalInsecureRandom.current());
        keypair = keyGen.generateKeyPair();



        /*
        if (getPrivateKey(context).isEmpty()) {
            kotlin.Pair<PrivKey, PubKey> keys = KeyKt.generateKeyPair(KEY_TYPE.ED25519);
            Base64.Encoder encoder = Base64.getEncoder();
            setPrivateKey(context, encoder.encodeToString(keys.getFirst().bytes()));
        }*/


        /* TODO

        node.setAgent(AGENT);
        node.setPushing(false);
        node.setEnableReachService(false);*/

        int checkPort = getPort(context);
        if (isLocalPortFree(checkPort)) {
            port = checkPort;
        } else {
            port = nextFreePort();
        }
        // byte[] data = Base64.getDecoder().decode(getPrivateKey(context));

        privateKey = new RsaPrivateKey(keypair.getPrivate(), keypair.getPublic());
        //privateKey = Ed25519Kt.unmarshalEd25519PrivateKey(data);
        LiteSignedCertificate selfSignedCertificate = new LiteSignedCertificate(privateKey, keypair);

        CLIENT_SSL_INSTANCE = QuicSslContextBuilder.forClient(
                selfSignedCertificate.privateKey(), null, selfSignedCertificate.certificate()).
                trustManager(new SimpleTrustManagerFactory() {
                    @Override
                    protected void engineInit(KeyStore keyStore) throws Exception {

                    }

                    @Override
                    protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws Exception {

                    }

                    @Override
                    protected TrustManager[] engineGetTrustManagers() {
                        return new TrustManager[]{tm};
                    }
                }).
                applicationProtocols("libp2p").build();

        SERVER_SSL_INSTANCE = QuicSslContextBuilder.forServer(
                selfSignedCertificate.privateKey(), null, selfSignedCertificate.certificate())
                .applicationProtocols("libp2p").clientAuth(ClientAuth.REQUIRE).
                        trustManager(new SimpleTrustManagerFactory() {
                            @Override
                            protected void engineInit(KeyStore keyStore) throws Exception {

                            }

                            @Override
                            protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws Exception {

                            }

                            @Override
                            protected TrustManager[] engineGetTrustManagers() {
                                return new TrustManager[]{tm};
                            }
                        }).build();


        int alpha = getConcurrencyValue(context);


        BlockStore blockstore = BlockStore.NewBlockstore(blocks);
        this.liteHost = new LiteHost(privateKey, blockstore, port, alpha);
        //this.liteHost.start(port); // TODO


        if (IPFS.CONNECTION_SERVICE_ENABLED) {
            liteHost.addConnectionHandler(conn -> connected(conn.remoteId()));
        }

    }

    public boolean canHop(@NonNull PeerId peerId, @NonNull Closeable closeable)
            throws ConnectionIssue, ClosedException {
        return liteHost.canHop(closeable, peerId);
    }

    @NonNull
    public Reachable getReachable() {
        return reachable;
    }

    void setReachable(@NonNull Reachable reachable) {
        this.reachable = reachable;
    }


    @NonNull
    public PeerId getPeerId(@NonNull String name) {
        return decode(name);
    }


    @NonNull
    public List<Multiaddr> listenAddresses() {
        return liteHost.listenAddresses();
    }

    public int getPort() {
        return port;
    }


    @NonNull
    public Cid storeFile(@NonNull File target) throws Exception {
        try (FileInputStream inputStream = new FileInputStream(target)) {
            return storeInputStream(inputStream);
        }
    }

    public Reachable evaluateReachable() {
        reachable = Reachable.UNKNOWN;
        try {


            AtomicInteger success = new AtomicInteger(0);
            AtomicInteger failed = new AtomicInteger(0);
            AtomicReference<String> result = new AtomicReference<>("");

            List<Connection> connections = liteHost.getConnections();
            for (Connection conn : connections) {
                try {
                    PeerInfo peerInfo = liteHost.getPeerInfo(new TimeoutCloseable(5), conn);
                    Multiaddr observed = peerInfo.getObserved();
                    if (observed != null) {
                        LogUtils.error(TAG, "ObservedAddress " + observed.toString());
                        if (Objects.equals(result.get(), observed.toString())) {
                            int value = success.incrementAndGet();
                            if (value >= 3) {
                                break;
                            }
                        } else {
                            result.set(observed.toString());
                            success.set(0);
                            int value = failed.incrementAndGet();
                            if (value >= 3) {
                                break;
                            }
                        }
                    }
                } catch (Throwable ignore) {
                    // ignore
                }
            }
            if (success.get() >= 3) {
                reachable = Reachable.PUBLIC;
            } else if (failed.get() >= 3) {
                reachable = Reachable.PRIVATE;
            } else {
                reachable = Reachable.UNKNOWN; // not 100 percent safe (but who cares)
            }

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            reachable = Reachable.UNKNOWN;
        }
        return reachable;
    }

    public void provide(@NonNull Cid cid, @NonNull Closeable closable) throws ClosedException {

        try {
            liteHost.getRouting().Provide(closable, cid);
        } catch (ClosedException closedException) {
            throw closedException;
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void rm(@NonNull Cid cid, boolean recursively) {
        try {
            Stream.Rm(() -> false, blocks, cid, recursively);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    @NonNull
    public Cid storeData(@NonNull byte[] data) throws IOException {

        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            return storeInputStream(inputStream);
        }
    }

    @NonNull
    public Cid storeText(@NonNull String content) throws IOException {

        try (InputStream inputStream = new ByteArrayInputStream(content.getBytes())) {
            return storeInputStream(inputStream);
        }
    }

    public void storeToFile(@NonNull File file, @NonNull Cid cid, @NonNull Closeable closeable) throws Exception {

        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            storeToOutputStream(fileOutputStream, cid, closeable);
        }
    }

    @NonNull
    public Cid storeInputStream(@NonNull InputStream inputStream,
                                @NonNull Progress progress, long size) {

        return Stream.Write(blocks, new WriterStream(inputStream, progress, size));

    }

    @NonNull
    public Cid storeInputStream(@NonNull InputStream inputStream) {

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
    public String getText(@NonNull Cid cid, @NonNull Closeable closeable) throws IOException, ClosedException {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            getToOutputStream(outputStream, cid, closeable);
            return new String(outputStream.toByteArray());
        }
    }

    public void storeToOutputStream(@NonNull OutputStream os, @NonNull Cid cid,
                                    @NonNull Progress progress) throws ClosedException, IOException {

        long totalRead = 0L;
        int remember = 0;

        io.ipfs.utils.Reader reader = getReader(cid, progress);
        long size = reader.getSize();
        byte[] buf = reader.loadNextData();
        while (buf != null && buf.length > 0) {

            if (progress.isClosed()) {
                throw new ClosedException();
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

    public void storeToOutputStream(@NonNull OutputStream os, @NonNull Cid cid,
                                    @NonNull Closeable closeable) throws ClosedException, IOException {

        Reader reader = getReader(cid, closeable);
        byte[] buf = reader.loadNextData();
        while (buf != null && buf.length > 0) {

            os.write(buf, 0, buf.length);
            buf = reader.loadNextData();
        }
    }

    @NonNull
    public byte[] getData(@NonNull Cid cid, @NonNull Progress progress) throws IOException, ClosedException {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            storeToOutputStream(outputStream, cid, progress);
            return outputStream.toByteArray();
        }
    }

    @NonNull
    public byte[] getData(@NonNull Cid cid, @NonNull Closeable closeable) throws IOException, ClosedException {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            storeToOutputStream(outputStream, cid, closeable);
            return outputStream.toByteArray();
        }
    }

    @Nullable
    public Cid rmLinkFromDir(@NonNull Cid dir, String name) {
        try {
            return Stream.RemoveLinkFromDir(blocks, () -> false, dir, name);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return null;
    }

    @Nullable
    public Cid addLinkToDir(@NonNull Cid dir, @NonNull String name, @NonNull Cid link) {
        try {
            return Stream.AddLinkToDir(blocks, () -> false, dir, name, link);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return null;
    }

    @Nullable
    public Cid createEmptyDir() {
        try {
            return Stream.CreateEmptyDir(blocks);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return null;
    }

    @NonNull
    public String decodeName(@NonNull String name) {
        try {
            PeerId peerId = decode(name);
            return peerId.toBase58();
        } catch (Throwable ignore) {
            // common use case to fail
        }
        return "";
    }

    @NonNull
    public PeerId getPeerID() {
        return liteHost.Self();
    }

    @NonNull
    public PeerInfo getPeerInfo(@NonNull PeerId peerId, @NonNull Closeable closeable)
            throws ClosedException, ConnectionIssue {

        Connection conn = liteHost.connect(closeable, peerId);

        return liteHost.getPeerInfo(closeable, conn);
    }

    public void shutdown() {
        try {
            liteHost.shutdown();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    @NonNull
    public Set<PeerId> connectedPeers() {

        Set<PeerId> peers = new HashSet<>();

        try {
            for (Connection connection : liteHost.getConnections()) {
                peers.add(connection.remoteId());
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return peers;
    }

    public boolean swarmConnect(@NonNull String multiAddress, int timeout) {
        try {
            return swarmConnect(multiAddress, new TimeoutCloseable(timeout));
        } catch (ClosedException ignore) {
            // ignore
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable.getMessage());
        }
        return false;
    }

    @Nullable
    public Node resolveNode(@NonNull Cid root, @NonNull List<String> path, @NonNull Closeable closeable) throws ClosedException {

        String resultPath = IPFS_PATH + root.String();
        for (String name : path) {
            resultPath = resultPath.concat("/").concat(name);
        }

        return resolveNode(resultPath, closeable);

    }

    @Nullable
    public Node resolveNode(@NonNull String path, @NonNull Closeable closeable) throws ClosedException {

        try {
            return Resolver.resolveNode(closeable, blocks, liteHost.getExchange(), path);
        } catch (ClosedException closedException) {
            throw closedException;
        } catch (Throwable ignore) {
            // common exception to not resolve a a path
        }
        return null;
    }

    @NonNull
    public String base32(@NonNull PeerId peerId) {
        try {
            return Stream.base32(peerId);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void bootstrap() {

        if (numConnections() < MIN_PEERS) {

            try {
                Set<String> addresses = DnsResolver.resolveDnsAddress(LIB2P_DNS);
                addresses.addAll(IPFS.IPFS_BOOTSTRAP_NODES);

                Set<PeerId> peers = new HashSet<>();
                for (String multiAddress : addresses) {
                    try {
                        Multiaddr multiaddr = new Multiaddr(multiAddress);
                        String name = multiaddr.getStringComponent(Protocol.P2P);
                        Objects.requireNonNull(name);
                        PeerId peerId = decode(name);
                        Objects.requireNonNull(peerId);

                        AddrInfo addrInfo = AddrInfo.create(peerId, multiaddr);
                        if (addrInfo.hasAddresses()) {
                            peers.add(peerId);
                            liteHost.protectPeer(peerId);
                            liteHost.addAddrs(addrInfo);
                        }
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                }


                List<Callable<Boolean>> tasks = new ArrayList<>();
                ExecutorService executor = Executors.newFixedThreadPool(TIMEOUT_BOOTSTRAP);
                for (PeerId peerId : peers) {
                    tasks.add(() -> liteHost.connectTo(new TimeoutCloseable(TIMEOUT_BOOTSTRAP), peerId));
                }

                List<Future<Boolean>> futures = executor.invokeAll(tasks, TIMEOUT_BOOTSTRAP, TimeUnit.SECONDS);
                for (Future<Boolean> future : futures) {
                    LogUtils.info(TAG, "\nBootstrap done " + future.isDone());
                }

                liteHost.getRouting().bootstrap();
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            } finally {
                LogUtils.info(TAG, "NumPeers " + numConnections());
            }


        }

    }

    @NonNull
    public String getBase32PeerId() {
        return base32(getPeerID());
    }


    public void publishName(@NonNull Cid cid, int sequence, @NonNull Closeable closeable)
            throws ClosedException {

        try {
            liteHost.PublishName(closeable, privateKey, IPFS_PATH + cid.String(), getPeerID(), sequence);
        } catch (ClosedException closedException) {
            throw closedException;
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void clearDatabase() {
        blocks.clear();
    }


    public void findProviders(@NonNull Routing.Providers providers,
                              @NonNull Cid cid,
                              @NonNull Closeable closeable) throws ClosedException {
        try {
            liteHost.findProviders(closeable, providers, cid);
        } catch (ClosedException closedException) {
            throw closedException;
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    @NonNull
    public Multiaddr remoteAddress(@NonNull PeerId peerId, @NonNull Closeable closeable)
            throws ClosedException, ConnectionIssue {
        return liteHost.connect(closeable, peerId).remoteAddress();
    }

    @Nullable
    public Cid resolve(@NonNull Cid root, @NonNull List<String> path,
                       @NonNull Closeable closeable) throws ClosedException {

        String resultPath = IPFS_PATH + root.String();
        for (String name : path) {
            resultPath = resultPath.concat("/").concat(name);
        }

        return resolve(resultPath, closeable);

    }

    @Nullable
    public Cid resolve(@NonNull String path, @NonNull Closeable closeable) throws ClosedException {

        try {
            Node node = Resolver.resolveNode(closeable, blocks, liteHost.getExchange(), path);
            if (node != null) {
                return node.Cid();
            }
        } catch (ClosedException closedException) {
            throw closedException;
        } catch (Throwable ignore) {
            // common use case not resolve a a path
        }
        return null;
    }

    public boolean resolve(@NonNull Cid cid, @NonNull String name,
                           @NonNull Closeable closeable) throws ClosedException {
        Cid res = resolve(IPFS_PATH + cid.String() + "/" + name, closeable);
        return res != null;
    }

    public boolean isDir(@NonNull Cid cid, @NonNull Closeable closeable) throws ClosedException {

        boolean result;
        try {
            BlockStore blockstore = BlockStore.NewBlockstore(blocks);
            result = Stream.IsDir(closeable, blockstore, liteHost.getExchange(), cid);
        } catch (ClosedException closedException) {
            throw closedException;
        } catch (Throwable e) {
            result = false;
        }
        return result;
    }

    public long getSize(@NonNull Cid cid, @NonNull Closeable closeable) throws ClosedException {
        List<Link> links = ls(cid, true, closeable);
        int size = -1;
        if (links != null) {
            for (Link info : links) {
                size += info.getSize();
            }
        }
        return size;
    }

    @Nullable
    public List<Link> links(@NonNull Cid cid, @NonNull Closeable closeable) throws ClosedException {

        List<Link> links = ls(cid, false, closeable);
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
    public List<Link> getLinks(@NonNull Cid cid, @NonNull Closeable closeable) throws ClosedException {

        List<Link> links = ls(cid, true, closeable);
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
    public List<Link> ls(@NonNull Cid cid, boolean resolveChildren,
                         @NonNull Closeable closeable) throws ClosedException {

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
            }, blockstore, liteHost.getExchange(), cid, resolveChildren);

        } catch (ClosedException closedException) {
            throw closedException;
        } catch (Throwable e) {
            return null;
        }
        return infoList;
    }

    @NonNull
    public Reader getReader(@NonNull Cid cid, @NonNull Closeable closeable) throws ClosedException {
        BlockStore blockstore = BlockStore.NewBlockstore(blocks);
        return Reader.getReader(closeable, blockstore, liteHost.getExchange(), cid);
    }

    private void getToOutputStream(@NonNull OutputStream outputStream, @NonNull Cid cid,
                                   @NonNull Closeable closeable) throws ClosedException, IOException {
        try (InputStream inputStream = getInputStream(cid, closeable)) {
            IPFS.copy(inputStream, outputStream);
        }
    }

    @NonNull
    public InputStream getLoaderStream(@NonNull Cid cid, @NonNull Closeable closeable) throws ClosedException {
        Reader loader = getReader(cid, closeable);
        return new ReaderStream(loader);
    }

    @NonNull
    public InputStream getLoaderStream(@NonNull Cid cid, @NonNull Progress progress) throws ClosedException {
        Reader loader = getReader(cid, progress);
        return new ProgressStream(loader, progress);

    }


    @Nullable
    public ResolvedName resolveName(@NonNull String name, long last,
                                    @NonNull Closeable closeable) throws ClosedException {

        LogUtils.info(TAG, "resolveName " + name);
        long time = System.currentTimeMillis();

        AtomicReference<ResolvedName> resolvedName = new AtomicReference<>(null);
        try {
            AtomicLong timeout = new AtomicLong(System.currentTimeMillis() + RESOLVE_MAX_TIME);

            PeerId id = decode(name);
            byte[] ipns = IPFS.IPNS_PATH.getBytes();
            byte[] ipnsKey = Bytes.concat(ipns, id.getBytes());

            liteHost.getRouting().SearchValue(() -> (timeout.get() < System.currentTimeMillis())
                    || closeable.isClosed(), new Routing.ResolveInfo() {

                private void setName(@NonNull String hash, long sequence) {
                    resolvedName.set(new ResolvedName(sequence,
                            hash.replaceFirst(IPFS_PATH, "")));
                }

                @Override
                public void resolved(byte[] data) {

                    try {
                        Ipns.IpnsEntry entry = Ipns.IpnsEntry.parseFrom(data);
                        Objects.requireNonNull(entry);
                        String hash = entry.getValue().toStringUtf8();
                        long seq = entry.getSequence();

                        LogUtils.info(TAG, "IpnsEntry : " + seq + " " + hash + " " +
                                (System.currentTimeMillis() - time));

                        if (seq < last) {
                            // newest value already available
                            throw new ClosedException();

                        }

                        if (hash.startsWith(IPFS_PATH)) {
                            timeout.set(System.currentTimeMillis() + RESOLVE_TIMEOUT);
                            setName(hash, seq);
                        } else {
                            LogUtils.info(TAG, "invalid hash " + hash);
                        }

                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }

                }
            }, ipnsKey, 8);

        } catch (ClosedException ignore) {
            // ignore exception here
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        LogUtils.info(TAG, "Finished resolve name " + name + " " +
                (System.currentTimeMillis() - time));

        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        return resolvedName.get();
    }

    @NonNull
    public InputStream getInputStream(@NonNull Cid cid, @NonNull Closeable closeable) throws ClosedException {
        Reader reader = getReader(cid, closeable);
        return new ReaderStream(reader);
    }

    public boolean isValidCID(@NonNull String cid) {
        try {
            return !Cid.Decode(cid).String().isEmpty();
        } catch (Throwable e) {
            return false;
        }
    }

    public long numConnections() {
        return liteHost.numConnections();
    }

    public void reset() {
        try {

            liteHost.getExchange().reset();

            liteHost.trimConnections();

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void load(@NonNull Cid cid, @NonNull Closeable closeable) {
        try {
            liteHost.getExchange().load(closeable, cid);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public boolean isConnected(@NonNull PeerId peerId) {
        return liteHost.isConnected(peerId);
    }


    public boolean notify(@NonNull PeerId peerId, @NonNull String content) {

        try {
            // TODO create a push message from content
            Connection conn = liteHost.connect(new TimeoutCloseable(TIMEOUT_PUSH), peerId);
            liteHost.send(new TimeoutCloseable(TIMEOUT_PUSH), PUSH_PROTOCOL, conn,
                    null /* TODO protocol message */);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return false;
    }

    public void setPusher(@Nullable Pusher pusher) {
        this.pusher = pusher;
    }

    @Override
    public boolean acceptPusher(@NonNull PeerId peerId) {
        return swarm.contains(peerId);
    }

    @Override
    public void pushMessage(@NonNull PeerId remotePeerId, @NonNull byte[] toByteArray) {
        if (pusher != null) {
            push(remotePeerId, new String(toByteArray));
        }
    }

    public void swarmReduce(@NonNull PeerId peerId) {
        swarm.remove(peerId);
    }

    public void swarmEnhance(@NonNull PeerId peerId) {
        swarm.add(peerId);
    }

    public void push(@NonNull PeerId peerId, @NonNull String content) {
        try {
            Objects.requireNonNull(peerId);
            Objects.requireNonNull(content);

            if (pusher != null) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.submit(() -> pusher.push(peerId, content));
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }

    public void swarmEnhance(@NonNull PeerId[] peerIds) {
        for (PeerId peerId : peerIds) {
            swarmEnhance(peerId);
        }
    }

    public void setConnector(@Nullable Connector connector) {
        this.connector = connector;
    }


    public void connected(@NonNull PeerId peerId) {
        if (swarm.contains(peerId)) {
            if (connector != null) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.submit(() -> connector.connected(peerId));
            }
        }
    }

    public boolean swarmConnect(@NonNull String multiAddress,
                                @NonNull Closeable closeable) throws ClosedException {


        Multiaddr multiaddr = new Multiaddr(multiAddress);
        String name = multiaddr.getStringComponent(Protocol.P2P);
        Objects.requireNonNull(name);
        PeerId peerId = decode(name);
        Objects.requireNonNull(peerId);

        try {
            liteHost.protectPeer(peerId);
            if (multiAddress.startsWith(IPFS.P2P_PATH)) {
                Set<Multiaddr> addrInfo = liteHost.getAddresses(peerId);
                if (addrInfo.isEmpty()) {
                    return liteHost.getRouting().FindPeer(closeable, peerId);
                } else {
                    return liteHost.connectTo(closeable, peerId);
                }
            } else {
                AddrInfo addrInfo = AddrInfo.create(peerId, multiaddr);
                liteHost.addAddrs(addrInfo);
                return liteHost.connectTo(closeable, peerId);
            }

        } catch (ClosedException closedException) {
            throw closedException;
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            LogUtils.error(TAG, multiaddr + " " + e.getClass().getName());
        }

        return false;
    }

    public interface Connector {
        void connected(@NonNull PeerId peerId);
    }

    public interface Pusher {
        void push(@NonNull PeerId peerId, @NonNull String content);
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

    @NonNull
    public LiteHost getHost() {
        return liteHost;
    }

    public PrivKey getPrivKey() {
        return privateKey;
    }

}
