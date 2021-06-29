package threads.lite;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;

import net.luminis.quic.QuicClientConnection;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import identify.pb.IdentifyOuterClass;
import threads.lite.cid.Cid;
import threads.lite.cid.Multiaddr;
import threads.lite.cid.PeerId;
import threads.lite.cid.Protocol;
import threads.lite.core.Closeable;
import threads.lite.core.ClosedException;
import threads.lite.core.ConnectionIssue;
import threads.lite.core.Progress;
import threads.lite.core.TimeoutCloseable;
import threads.lite.crypto.PrivKey;
import threads.lite.crypto.Rsa;
import threads.lite.data.BLOCKS;
import threads.lite.dht.Routing;
import threads.lite.format.BlockStore;
import threads.lite.format.Node;
import threads.lite.host.AddrInfo;
import threads.lite.host.DnsResolver;
import threads.lite.host.LiteHost;
import threads.lite.host.LiteHostCertificate;
import threads.lite.host.PeerInfo;
import threads.lite.ipns.Ipns;
import threads.lite.push.Push;
import threads.lite.push.PushService;
import threads.lite.utils.Link;
import threads.lite.utils.LinkCloseable;
import threads.lite.utils.ProgressStream;
import threads.lite.utils.Reachable;
import threads.lite.utils.Reader;
import threads.lite.utils.ReaderProgress;
import threads.lite.utils.ReaderStream;
import threads.lite.utils.Resolver;
import threads.lite.utils.Stream;
import threads.lite.utils.WriterStream;

public class IPFS {

    public static final String TimeFormatIpfs = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS'Z'";  // RFC3339Nano = "2006-01-02T15:04:05.999999999Z07:00"
    public static final String RELAY_RENDEZVOUS = "/libp2p/relay";
    public static final String RELAY_PROTOCOL = "/libp2p/circuit/relay/0.1.0";
    public static final String DHT_PROTOCOL = "/ipfs/kad/1.0.0";
    public static final String PUSH_PROTOCOL = "/ipfs/push/1.0.0";
    public static final String STREAM_PROTOCOL = "/multistream/1.0.0";
    public static final String BITSWAP_PROTOCOL = "/ipfs/bitswap/1.2.0";
    public static final String IDENTITY_PROTOCOL = "/ipfs/id/1.0.0";
    public static final String INDEX_HTML = "index.html";
    public static final String AGENT_PREFIX = "lite";
    public static final String AGENT = AGENT_PREFIX + "/0.7.4/";
    public static final String PROTOCOL_VERSION = "ipfs/0.1.0";
    public static final String IPFS_PATH = "/ipfs/";
    public static final String IPNS_PATH = "/ipns/";
    public static final String P2P_PATH = "/p2p/";
    public static final String LIB2P_DNS = "bootstrap.libp2p.io"; // IPFS BOOTSTRAP DNS
    public static final String NA = "na";
    public static final String LS = "ls";
    public static final String APRN = "libp2p";


    public static final int PRELOAD = 25;
    public static final int PRELOAD_DIST = 5;
    public static final int CHUNK_SIZE = 262144;
    public static final int BLOCK_SIZE_LIMIT = 1048576; // 1 MB
    public static final long RESOLVE_MAX_TIME = 30000; // 30 sec
    public static final boolean SEND_DONT_HAVES = false;
    public static final boolean BITSWAP_ENGINE_ACTIVE = false;
    public static final int PROTOCOL_READER_LIMIT = 1000;
    public static final int TIMEOUT_BOOTSTRAP = 10;
    public static final int MAX_STREAMS = 100;
    public static final int GRACE_PERIOD = 10;
    public static final int MIN_PEERS = 5;
    public static final int RESOLVE_TIMEOUT = 1000; // 1 sec
    public static final long WANTS_WAIT_TIMEOUT = 500; // 500 ms
    public static final boolean EVALUATE_PEER = false;
    public static final short PRIORITY_URGENT = 1;
    public static final short PRIORITY_HIGH = 5;
    public static final short PRIORITY_NORMAL = 10;


    // MessageSizeMax is a soft (recommended) maximum for network messages.
    // One can write more, as the interface is a stream. But it is useful
    // to bunch it up into multiple read/writes when the whole message is
    // a single, large serialized object.
    public static final int MESSAGE_SIZE_MAX = 1 << 22; // 4 MB


    @NonNull
    public static final List<String> DHT_BOOTSTRAP_NODES = new ArrayList<>(Arrays.asList(
            "/dnsaddr/bootstrap.libp2p.io/p2p/QmNnooDu7bfjPFoTZYxMNLWUQJyrVwtbZg5gBMjTezGAJN", // default dht peer
            "/dnsaddr/bootstrap.libp2p.io/p2p/QmQCU2EcMqAqQPR2i9bChDtGNJchTbq5TbXJJ16u19uLTa", // default dht peer
            "/dnsaddr/bootstrap.libp2p.io/p2p/QmbLHAnMoJPWSCR5Zhtx6BHJX9KiKNN6tpvbUcqanj75Nb", // default dht peer
            "/dnsaddr/bootstrap.libp2p.io/p2p/QmcZf59bWwK5XFi76CZX8cbJ4BhTzzA3gU1ZjYZcYW3dwt" // default dht peer

    ));
    // IPFS BOOTSTRAP
    @NonNull
    public static final List<String> IPFS_BOOTSTRAP_NODES = new ArrayList<>(Collections.singletonList(
            "/ip4/104.131.131.82/tcp/4001/p2p/QmaCpDMGvV2BGHeYERUEnRQAwe3N8SzbUtfsmvsqQLuvuJ" // mars.i.ipfs.io
    ));
    @NonNull
    public static final List<String> IPFS_RELAYS_NODES = new ArrayList<>(Arrays.asList(
            "/ip4/147.75.195.153/tcp/4001/p2p/QmW9m57aiBDHAkKj9nmFSEn7ZqrcF1fZS4bipsTCHburei",// default relay  libp2p
            "/ip4/147.75.70.221/tcp/4001/p2p/Qme8g49gm3q4Acp7xWBKg3nAa9fxZ1YmyDJdyGgoG6LsXh"// default relay  libp2p
    ));

    public static final int KAD_DHT_BUCKET_SIZE = 20;
    public static final int KAD_DHT_BETA = 30;
    public static final int CONNECT_TIMEOUT = 5;
    public static final int BITSWAP_LOAD_PROVIDERS_REFRESH = 10000;
    public static final long DHT_REQUEST_READ_TIMEOUT = 5;
    public static final long IPNS_DURATION = 6; // 6 hours duration
    public static final int THRESHOLD_FIND_PROVIDERS = 5;
    public static final boolean BITSWAP_REQUEST_ACTIVE = true;

    private static final String SWARM_PORT_KEY = "swarmPortKey";
    private static final String PRIVATE_KEY = "privateKey";
    private static final String PUBLIC_KEY = "publicKey";
    private static final String CONCURRENCY_KEY = "concurrencyKey";
    private static final String TAG = IPFS.class.getSimpleName();
    private static final String PREF_KEY = IPFS.TAG;
    private static final boolean CONNECTION_SERVICE_ENABLED = false;
    private static final boolean SERVER_ACTIVE = false;
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

    private static IPFS INSTANCE = null;

    static {
        Security.removeProvider("BC");
        // Confirm that positioning this provider at the end works for your needs!
        Security.addProvider(new BouncyCastleProvider());
    }

    @NonNull
    private final BLOCKS blocks;
    @NonNull
    private final LiteHost host;
    @NonNull
    private final PrivKey privateKey;
    private final int port;
    @NonNull
    private Reachable reachable = Reachable.UNKNOWN;
    @Nullable
    private Connector connector;

    private IPFS(@NonNull Context context) throws Exception {


        blocks = BLOCKS.getInstance(context);

        KeyPair keypair = getKeyPair(context);


        int checkPort = getPort(context);
        if (isLocalPortFree(checkPort)) {
            port = checkPort;
        } else {
            port = nextFreePort();
        }
        //privateKey = Rsa.generateRsaKeyPair(2048, new SecureRandom()).first;
        privateKey = new Rsa.RsaPrivateKey(keypair.getPrivate(), keypair.getPublic());
        LiteHostCertificate selfSignedCertificate = new LiteHostCertificate(context,
                privateKey, keypair);


        int alpha = getConcurrencyValue(context);


        BlockStore blockstore = BlockStore.createBlockStore(blocks);
        this.host = new LiteHost(selfSignedCertificate, privateKey, blockstore, port, alpha);


        if (IPFS.SERVER_ACTIVE) {
            this.host.start();
        }

        if (IPFS.CONNECTION_SERVICE_ENABLED) {
            host.addConnectionHandler(this::connected);
        }
    }


    private static void setPublicKey(@NonNull Context context, @NonNull String key) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PUBLIC_KEY, key);
        editor.apply();
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

    @NonNull
    private static String getPublicKey(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return Objects.requireNonNull(sharedPref.getString(PUBLIC_KEY, ""));

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

    private KeyPair getKeyPair(@NonNull Context context) throws NoSuchAlgorithmException, InvalidKeySpecException {

        if (!getPrivateKey(context).isEmpty() && !getPublicKey(context).isEmpty()) {

            Base64.Decoder decoder = Base64.getDecoder();

            byte[] privateKeyData = decoder.decode(getPrivateKey(context));
            byte[] publicKeyData = decoder.decode(getPublicKey(context));

            PublicKey publicKey = KeyFactory.getInstance("RSA").
                    generatePublic(new X509EncodedKeySpec(publicKeyData));
            PrivateKey privateKey = KeyFactory.getInstance("RSA").
                    generatePrivate(new PKCS8EncodedKeySpec(privateKeyData));

            return new KeyPair(publicKey, privateKey);

        } else {

            String algorithm = "RSA";
            final KeyPair keypair;

            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithm);
            keyGen.initialize(2048, LiteHostCertificate.ThreadLocalInsecureRandom.current());
            keypair = keyGen.generateKeyPair();

            Base64.Encoder encoder = Base64.getEncoder();
            setPrivateKey(context, encoder.encodeToString(keypair.getPrivate().getEncoded()));
            setPublicKey(context, encoder.encodeToString(keypair.getPublic().getEncoded()));
            return keypair;
        }
    }

    public boolean canHop(@NonNull PeerId peerId, @NonNull Closeable closeable)
            throws ConnectionIssue, ClosedException {
        return host.canHop(closeable, peerId);
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
        return PeerId.decodeName(name);
    }


    @NonNull
    public PeerInfo getIdentity() {

        IdentifyOuterClass.Identify identity = host.createIdentity(null);


        String agent = identity.getAgentVersion();
        String version = identity.getProtocolVersion();
        Multiaddr observedAddr = null;
        if (identity.hasObservedAddr()) {
            observedAddr = new Multiaddr(identity.getObservedAddr().toByteArray());
        }

        List<String> protocols = new ArrayList<>();
        List<Multiaddr> addresses = new ArrayList<>();
        List<ByteString> entries = identity.getProtocolsList().asByteStringList();
        for (ByteString entry : entries) {
            protocols.add(entry.toStringUtf8());
        }
        entries = identity.getListenAddrsList();
        for (ByteString entry : entries) {
            addresses.add(new Multiaddr(entry.toByteArray()));
        }

        return new PeerInfo(getPeerID(), agent, version, addresses, protocols, observedAddr);

    }

    @NonNull
    public List<Multiaddr> listenAddresses() {
        return host.listenAddresses();
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

            Set<PeerId> peers = host.getConnectedPeers();
            for (PeerId peerId : peers) {
                try {
                    PeerInfo peerInfo = host.getPeerInfo(new TimeoutCloseable(5), peerId);
                    Multiaddr observed = peerInfo.getObserved();
                    if (observed != null) {

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

    public void provide(@NonNull Cid cid, @NonNull Closeable closable) {
        try {
            host.getRouting().provide(closable, cid);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void rm(@NonNull Cid cid) {
        try {
            Stream.removeCid(() -> false, blocks, cid);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
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

        return Stream.write(blocks, new WriterStream(inputStream, progress, size));

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
            return outputStream.toString();
        }
    }

    public void storeToOutputStream(@NonNull OutputStream os, @NonNull Cid cid,
                                    @NonNull Progress progress) throws ClosedException, IOException {

        long totalRead = 0L;
        int remember = 0;

        threads.lite.utils.Reader reader = getReader(cid, progress);
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
            return Stream.removeLinkFromDir(blocks, () -> false, dir, name);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return null;
    }

    @Nullable
    public Cid addLinkToDir(@NonNull Cid dir, @NonNull String name, @NonNull Cid link) {
        try {
            return Stream.addLinkToDir(blocks, () -> false, dir, name, link);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return null;
    }

    @Nullable
    public Cid createEmptyDir() {
        try {
            return Stream.createEmptyDir(blocks);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return null;
    }

    @NonNull
    public String decodeName(@NonNull String name) {
        try {
            PeerId peerId = PeerId.decodeName(name);
            return peerId.toBase58();
        } catch (Throwable ignore) {
            // common use case to fail
        }
        return "";
    }

    @NonNull
    public PeerId getPeerID() {
        return host.self();
    }

    @NonNull
    public PeerInfo getPeerInfo(@NonNull PeerId peerId, @NonNull Closeable closeable)
            throws ClosedException, ConnectionIssue {
        return host.getPeerInfo(closeable, peerId);
    }

    public void shutdown() {
        try {
            host.shutdown();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    @NonNull
    public Set<PeerId> connectedPeers() {
        return host.getConnectedPeers();
    }


    public boolean swarmConnect(@NonNull String multiAddress, int timeout) {
        try {
            return swarmConnect(new TimeoutCloseable(timeout), multiAddress, timeout);
        } catch (ClosedException | ConnectionIssue ignore) {
            // ignore
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
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
            return Resolver.resolveNode(closeable, blocks, host.getBitSwap(), path);
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
            return peerId.toBase32();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void bootstrap() {
        synchronized (IPFS.TAG.intern()) {
            if (numConnections() < MIN_PEERS) {
                try {
                    this.host.getRouting().bootstrap();
                    this.host.relays();

                    Set<String> addresses = DnsResolver.resolveDnsAddress(LIB2P_DNS);
                    addresses.addAll(IPFS.DHT_BOOTSTRAP_NODES);
                    addresses.addAll(IPFS.IPFS_BOOTSTRAP_NODES);

                    Set<PeerId> peers = new HashSet<>();
                    for (String address : addresses) {
                        try {
                            Multiaddr multiaddr = new Multiaddr(address);
                            String name = multiaddr.getStringComponent(Protocol.Type.P2P);
                            Objects.requireNonNull(name);
                            PeerId peerId = PeerId.fromBase58(name);
                            Objects.requireNonNull(peerId);

                            AddrInfo addrInfo = AddrInfo.create(peerId, multiaddr, false);
                            if (addrInfo.hasAddresses()) {
                                peers.add(peerId);
                                host.protectPeer(peerId, host.getMaxTime());
                                host.addToAddressBook(addrInfo);
                            }
                        } catch (Throwable throwable) {
                            LogUtils.error(TAG, throwable);
                        }
                    }


                    ExecutorService executor = Executors.newFixedThreadPool(TIMEOUT_BOOTSTRAP);
                    for (PeerId peerId : peers) {
                        executor.execute(() -> {
                            try {
                                host.connect(new TimeoutCloseable(TIMEOUT_BOOTSTRAP), peerId,
                                        TIMEOUT_BOOTSTRAP);
                            } catch (ConnectionIssue ignore) {
                                // ignore
                            } catch (Throwable throwable) {
                                LogUtils.error(TAG, throwable);
                            }
                        });
                    }
                    executor.awaitTermination(TIMEOUT_BOOTSTRAP, TimeUnit.SECONDS);


                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    LogUtils.debug(TAG, "NumPeers " + numConnections());
                }
            }
        }
    }

    @NonNull
    public String getBase32PeerId() {
        return base32(getPeerID());
    }


    public void publishName(@NonNull Cid cid, int sequence, @NonNull Closeable closeable) {

        try {
            host.PublishName(closeable, privateKey, IPFS_PATH + cid.String(), getPeerID(), sequence);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void clearDatabase() {
        blocks.clear();
    }


    public void findProviders(@NonNull Routing.Providers providers,
                              @NonNull Cid cid,
                              @NonNull Closeable closeable) {
        try {
            host.findProviders(closeable, providers, cid);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    @NonNull
    public Multiaddr remoteAddress(@NonNull PeerId peerId, @NonNull Closeable closeable)
            throws ClosedException, ConnectionIssue {
        return host.transform(host.connect(closeable, peerId, IPFS.CONNECT_TIMEOUT)
                .getRemoteAddress());
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
            Node node = Resolver.resolveNode(closeable, blocks, host.getBitSwap(), path);
            if (node != null) {
                return node.getCid();
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
            BlockStore blockstore = BlockStore.createBlockStore(blocks);
            result = Stream.isDir(closeable, blockstore, host.getBitSwap(), cid);
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
            BlockStore blockstore = BlockStore.createBlockStore(blocks);
            Stream.ls(new LinkCloseable() {

                @Override
                public boolean isClosed() {
                    return closeable.isClosed();
                }

                @Override
                public void info(@NonNull Link link) {
                    infoList.add(link);
                }
            }, blockstore, host.getBitSwap(), cid, resolveChildren);

        } catch (ClosedException closedException) {
            throw closedException;
        } catch (Throwable e) {
            return null;
        }
        return infoList;
    }

    @NonNull
    public Reader getReader(@NonNull Cid cid, @NonNull Closeable closeable) throws ClosedException {
        BlockStore blockstore = BlockStore.createBlockStore(blocks);
        return Reader.getReader(closeable, blockstore, host.getBitSwap(), cid);
    }

    private void getToOutputStream(@NonNull OutputStream outputStream, @NonNull Cid cid,
                                   @NonNull Closeable closeable) throws ClosedException, IOException {
        try (InputStream inputStream = getInputStream(cid, closeable)) {
            IPFS.copy(inputStream, outputStream);
        }
    }

    public void setPusher(@Nullable Push push) {
        this.host.setPush(push);
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
    public Ipns.Entry resolveName(@NonNull String name, long last,
                                  @NonNull Closeable closeable) {


        long time = System.currentTimeMillis();

        AtomicReference<Ipns.Entry> resolvedName = new AtomicReference<>(null);
        try {
            AtomicLong timeout = new AtomicLong(System.currentTimeMillis() + RESOLVE_MAX_TIME);

            PeerId id = PeerId.decodeName(name);
            byte[] ipns = IPFS.IPNS_PATH.getBytes();
            byte[] ipnsKey = Bytes.concat(ipns, id.getBytes());

            host.getRouting().searchValue(
                    () -> (timeout.get() < System.currentTimeMillis()) || closeable.isClosed(),
                    entry -> {

                        String value = entry.getValue();
                        long sequence = entry.getSequence();

                        LogUtils.info(TAG, "IpnsEntry : " + entry.toString() +
                                (System.currentTimeMillis() - time));

                        if (sequence < last) {
                            // newest value already available
                            LogUtils.debug(TAG, "newest value " + sequence);
                            timeout.set(System.currentTimeMillis());
                            return;
                        }

                        if (value.startsWith(IPFS_PATH)) {
                            resolvedName.set(entry);
                            timeout.set(System.currentTimeMillis() + RESOLVE_TIMEOUT);
                        } else {
                            LogUtils.error(TAG, "invalid value " + value);
                        }

                    }, ipnsKey);

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }


        LogUtils.debug(TAG, "Finished resolve name " + name + " " +
                (System.currentTimeMillis() - time));


        return resolvedName.get();
    }

    @NonNull
    public InputStream getInputStream(@NonNull Cid cid, @NonNull Closeable closeable) throws ClosedException {
        Reader reader = getReader(cid, closeable);
        return new ReaderStream(reader);
    }

    public boolean isValidCID(@NonNull String cid) {
        try {
            return !Cid.decode(cid).String().isEmpty();
        } catch (Throwable e) {
            return false;
        }
    }

    public long numConnections() {
        return host.numConnections();
    }

    public void reset() {
        try {
            host.getBitSwap().reset();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public boolean isConnected(@NonNull PeerId peerId) {
        return host.isConnected(peerId);
    }


    @SuppressWarnings("unused")
    public boolean notify(@NonNull PeerId peerId, @NonNull String content) {

        try {
            QuicClientConnection conn = host.connect(new TimeoutCloseable(CONNECT_TIMEOUT), peerId,
                    CONNECT_TIMEOUT);

            PushService.notify(conn, content);

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return false;
    }


    public void swarmReduce(@NonNull PeerId peerId) {
        host.swarmReduce(peerId);
    }

    public void swarmEnhance(@NonNull PeerId peerId) {
        host.swarmEnhance(peerId);
    }


    public void swarmEnhance(@NonNull PeerId[] peerIds) {
        for (PeerId peerId : peerIds) {
            swarmEnhance(peerId);
        }
    }

    public void setConnector(@Nullable Connector connector) {
        this.connector = connector;
    }


    public Set<Multiaddr> getAddresses(@NonNull PeerId peerId) {
        return host.getAddresses(peerId);
    }

    public void connected(@NonNull PeerId peerId) {
        if (host.swarmContains(peerId)) {
            if (connector != null) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.submit(() -> connector.connected(peerId));
            }
        }
    }

    public boolean swarmConnect(@NonNull PeerId peerId, @NonNull Closeable closeable) {
        try {
            host.connectTo(closeable, peerId, IPFS.CONNECT_TIMEOUT);
        } catch (ClosedException | ConnectionIssue ignore) {
            // ignore
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return host.isConnected(peerId);
    }

    private boolean swarmConnect(@NonNull Closeable closeable,
                                 @NonNull String multiAddress,
                                 int timeout) throws ClosedException, ConnectionIssue {


        Multiaddr multiaddr = new Multiaddr(multiAddress);
        String name = multiaddr.getStringComponent(Protocol.Type.P2P);
        Objects.requireNonNull(name);
        PeerId peerId = PeerId.fromBase58(name);
        Objects.requireNonNull(peerId);

        try {
            host.protectPeer(peerId, host.getMaxTime());
            if (multiAddress.startsWith(IPFS.P2P_PATH)) {
                swarmConnect(peerId, closeable);
            } else {
                AddrInfo addrInfo = AddrInfo.create(peerId, multiaddr, true);
                host.addToAddressBook(addrInfo);
                host.connect(closeable, peerId, timeout);
                return true;
            }

        } catch (ClosedException | ConnectionIssue exception) {
            throw exception;
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        return host.isConnected(peerId);
    }

    @NonNull
    public LiteHost getHost() {
        return host;
    }

    public List<PeerId> getRelays() {
        return host.getRelays();
    }

    public void updateNetwork(@NonNull String networkInterface) {
        getHost().updateNetwork(networkInterface);
    }

    public interface Connector {
        void connected(@NonNull PeerId peerId);
    }


}
