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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.LogUtils;
import io.core.Closeable;
import io.core.ClosedException;
import io.core.TimeoutCloseable;
import io.dht.DhtProtocol;
import io.dht.KadDHT;
import io.dht.Providers;
import io.dht.ResolveInfo;
import io.dht.Routing;
import io.ipfs.bitswap.BitSwap;
import io.ipfs.bitswap.BitSwapMessage;
import io.ipfs.bitswap.BitSwapNetwork;
import io.ipfs.bitswap.BitSwapProtocol;
import io.ipfs.bitswap.LiteHost;
import io.ipfs.bitswap.Receiver;
import io.ipfs.cid.Cid;
import io.ipfs.exchange.Interface;
import io.ipfs.format.BlockStore;
import io.ipfs.format.Node;
import io.ipfs.multibase.Base58;
import io.ipfs.multibase.Multibase;
import io.ipfs.multihash.Multihash;
import io.ipfs.utils.Link;
import io.ipfs.utils.LinkCloseable;
import io.ipfs.utils.Progress;
import io.ipfs.utils.ProgressStream;
import io.ipfs.utils.ReaderProgress;
import io.ipfs.utils.ReaderStream;
import io.ipfs.utils.Resolver;
import io.ipfs.utils.Stream;
import io.ipns.IpnsValidator;
import io.libp2p.AddrInfo;
import io.libp2p.HostBuilder;
import io.libp2p.core.Connection;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multiformats.Protocol;
import io.libp2p.core.mux.StreamMuxerProtocol;
import io.libp2p.crypto.keys.Ed25519Kt;
import io.libp2p.protocol.Identify;
import io.libp2p.security.noise.NoiseXXSecureChannel;
import io.libp2p.security.secio.SecIoSecureChannel;
import io.libp2p.transport.tcp.TcpTransport;
import io.protos.ipns.IpnsProtos;
import threads.thor.core.blocks.BLOCKS;

public class IPFS implements Receiver {
    // TimeFormatIpfs is the format ipfs uses to represent time in string form.
    public static final String TimeFormatIpfs = "2006-01-02'T'15:04:05.999999999Z07:00";
    public static final int PRELOAD = 25;
    public static final int PRELOAD_DIST = 5;
    public static final int WRITE_TIMEOUT = 10;
    public static final String AGENT = "/go-ipfs/0.9.0-dev/thor"; // todo rename
    public static final String PROTOCOL_VERSION = "ipfs/0.1.0";  // todo rename
    public static final int TIMEOUT_BOOTSTRAP = 5;
    public static final long TIMEOUT_DHT_PEER = 3;
    public static final int LOW_WATER = 50;
    public static final int HIGH_WATER = 300;
    public static final String GRACE_PERIOD = "10s";
    public static final int MIN_PEERS = 10;
    public static final long RESOLVE_MAX_TIME = 20000; // 20 sec
    public static final int RESOLVE_TIMEOUT = 3000; // 3 sec
    public static final long WANTS_WAIT_TIMEOUT = 2000; // 2 sec
    public static final int CHUNK_SIZE = 262144;
    public static final String ProtocolBitswap = "/ipfs/bitswap/1.2.0";

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
    private static final String SWARM_PORT_KEY = "swarmPortKey";
    private static final String PUBLIC_KEY = "publicKey";
    private static final String PRIVATE_KEY = "privateKey";
    private static final String CONCURRENCY_KEY = "concurrencyKey";
    private static final String TAG = IPFS.class.getSimpleName();
    private static IPFS INSTANCE = null;

    private final BLOCKS blocks;
    private final Interface exchange;
    private final Routing routing;
    private final Host host;
    private final String privateKey;

    private boolean running;
    private final int port;


    private IPFS(@NonNull Context context) throws Exception {

        blocks = BLOCKS.getInstance(context);


        if (getPeerID(context) == null) {
            kotlin.Pair<PrivKey, PubKey> keys = KeyKt.generateKeyPair(KEY_TYPE.ED25519);
            java.util.Base64.Encoder encoder = java.util.Base64.getEncoder();
            setPublicKey(context, encoder.encodeToString(keys.getSecond().bytes()));
            setPrivateKey(context, encoder.encodeToString(keys.getFirst().bytes()));

            PeerId id = PeerId.fromPubKey(keys.getSecond());
            Objects.requireNonNull(id);
            setPeerID(context, id.toBase58());
        } else {
            String pk = IPFS.getPublicKey(context);

            byte[] data = Base64.getDecoder().decode(pk);
            PubKey pkwy = Ed25519Kt.unmarshalEd25519PublicKey(data);

            PeerId id = PeerId.fromPubKey(pkwy);
            if (!Objects.equals(id.toBase58(), IPFS.getPeerID(context))) {
                LogUtils.error(TAG, id.toBase58() + " " + IPFS.getPeerID(context));
            }
        }

        /* TODO
        node.setPeerID(IPFS.getPeerID(context));
        node.setPrivateKey(IPFS.getPrivateKey(context));
        node.setPublicKey(IPFS.getPublicKey(context));



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
        node.setEnableConnService(false);*/


        port = nextFreePort();
        privateKey = getPrivateKey(context);

        String prk = IPFS.getPrivateKey(context);
        byte[] kk = Base64.getDecoder().decode(prk);
        PrivKey privKey = KeyKt.unmarshalPrivateKey(kk);
        PeerId tt = PeerId.fromPubKey(privKey.publicKey());


        host = new HostBuilder()
                .protocol(new Identify(), new DhtProtocol(),
                        new BitSwapProtocol(this, ProtocolBitswap))
                .identity(privKey)
                .transport(TcpTransport::new) // TODO QUIC Transport when available
                .secureChannel(NoiseXXSecureChannel::new, SecIoSecureChannel::new) // TODO add TLS when available, and remove Secio
                .muxer(StreamMuxerProtocol::getMplex)
                .listen("/ip4/127.0.0.1/tcp/" + port  /* TODO QUIC + IPV6,
                        "/ip6/::/tcp/"+ port,
                        "/ip4/0.0.0.0/udp/"+port+"/quic",
                        "/ip6/::/udp/"+port+"/quic"*/)
                .build();

        int alpha = getConcurrencyValue(context);

        // TODO implement validator
        this.routing = new KadDHT(host, new IpnsValidator(), alpha);


        BitSwapNetwork bsm = LiteHost.NewLiteHost(host, routing);
        BlockStore blockstore = BlockStore.NewBlockstore(blocks);

        this.exchange = BitSwap.New(bsm, blockstore);
        host.start().get();
        running = true;


        String rk = IPFS.IPNS_PATH + new String(tt.getBytes());
        String cmp = IPFS.IPNS_PATH + new String(host.getPeerId().getBytes());
        LogUtils.error(TAG, rk);
        LogUtils.error(TAG, cmp);
        PeerId s = decode(Multibase.encode(Multibase.Base.Base32, host.getPeerId().getBytes()));
        String rks = IPFS.IPNS_PATH + new String(s.getBytes());
        LogUtils.error(TAG, rks);
        LogUtils.error(TAG, IPFS.IPNS_PATH + new String(decode(getPeerID()).getBytes()));
    }
    public int getPort(){
        return port;
    }

    @Override
    public void ReceiveMessage(@NonNull PeerId peer, @NonNull String protocol, @NonNull BitSwapMessage incoming) {
        exchange.ReceiveMessage(peer, protocol, incoming);
    }

    @Override
    public void ReceiveError(@NonNull PeerId peer, @NonNull String protocol, @NonNull String error) {
        exchange.ReceiveError(peer, protocol, error);
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

                for (Connection connection : host.getNetwork().getConnections()) {
                    peers.add(connection.secureSession().getRemoteId().toBase58()); // TODO return PeerId
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        }
        return peers;
    }

    public void dhtPublish(@NonNull Closeable closable, @NonNull String cid) throws ClosedException {

        if (!isDaemonRunning()) {
            return;
        }

        try {
            // TODO node.dhtProvide(cid, closable::isClosed);
        } catch (Throwable ignore) {
        }
        if (closable.isClosed()) {
            throw new ClosedException();
        }
    }


    public void Provide(@NonNull Closeable closeable, @NonNull Cid cid) {
        try {
            dhtPublish(closeable, cid.String());
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }

    }

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

    public void rm(@NonNull String cid, boolean recursively) {
        try {
            Stream.Rm(() -> false, blocks, cid, recursively);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
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

    @Nullable
    public byte[] loadData(@NonNull String cid, @NonNull Closeable closeable) throws Exception {
        if (!isDaemonRunning()) {
            return null;
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            storeToOutputStream(outputStream, new Progress() {
                @Override
                public void setProgress(int progress) {

                }

                @Override
                public boolean doProgress() {
                    return false;
                }

                @Override
                public boolean isClosed() {
                    return closeable.isClosed();
                }
            }, cid);
            return outputStream.toByteArray();
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

    @Override
    public boolean GatePeer(PeerId peerID) {
        return exchange.GatePeer(peerID);
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



    private static String deserialize(byte[] raw) {
        try (InputStream inputStream = new ByteArrayInputStream(raw)) {
            return deserialize(inputStream);
        } catch (Throwable ignore) {
            return "";
        }

    }

    private static String deserialize(InputStream din) throws IOException {
        int type = (int) Multihash.readVarint(din);
        if (type != 1) {
            throw new RuntimeException();
        }
        Multihash.readVarint(din);
        ByteBuffer byteBuffer = ByteBuffer.allocate(din.available());

        int res = din.read(byteBuffer.array());
        if (res <= 0) {
            throw new RuntimeException();
        }

        return Base58.encode(byteBuffer.array());
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
    public String decodeName(@NonNull String name) {
        try {
            return decode(name).toBase58();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return "";
    }


    @NonNull
    public String getPeerID() {
        return host.getPeerId().toBase58();
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

                    routing.init();


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
                } finally {
                    LogUtils.error(TAG, "NumPeers " + numSwarmPeers() );
                }
            }
        }
    }

    public void shutdown() {
        try {
            host.stop().get();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        } finally {
            running = false;
        }
    }

    public boolean swarmConnect(@NonNull String multiAddress,
                                @NonNull Closeable closeable) throws ClosedException {
        if (!isDaemonRunning()) {
            return false;
        }

        Multiaddr multiaddr = new Multiaddr(multiAddress);
        try {
            return host.getNetwork().connect(multiaddr).get() != null;
        } catch (Throwable e) {

            try {
                if (multiAddress.startsWith(IPFS.P2P_PATH)) {
                    String pid = multiaddr.getStringComponent(Protocol.P2P);
                    Objects.requireNonNull(pid);
                    AddrInfo addr = routing.FindPeer(closeable, PeerId.fromBase58(pid));
                    if(addr != null) {
                        return host.getNetwork().connect(addr.getPeerId(),
                                addr.getAddresses())
                                .get() != null;
                    }
                }
            } catch (ClosedException closedException) {
                throw closedException;
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
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
        Multiaddr multiaddr = new Multiaddr(multiAddress);
        try {
            return host.getNetwork().connect(multiaddr)
                    .get(timeout, TimeUnit.SECONDS) != null;
        } catch (Throwable e) {

            try {
                if (multiAddress.startsWith(IPFS.P2P_PATH)) {
                    String pid = multiaddr.getStringComponent(Protocol.P2P);
                    Objects.requireNonNull(pid);
                    AddrInfo addr = routing.FindPeer(new TimeoutCloseable(timeout), PeerId.fromBase58(pid));
                    if(addr != null) {
                        return host.getNetwork().connect(addr.getPeerId(),
                                addr.getAddresses())
                                .get(timeout, TimeUnit.SECONDS) != null;
                    }
                }
            } catch(ClosedException ignore) {
                // ignore
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }
        return false;
    }


    @Nullable
    public Node resolveNode(@NonNull String root, @NonNull List<String> path, @NonNull Closeable closeable) throws ClosedException {

        String resultPath = IPFS_PATH + root;
        for (String name : path) {
            resultPath = resultPath.concat("/").concat(name);
        }

        return resolveNode(resultPath, closeable);

    }

    @Nullable
    public Node resolveNode(@NonNull String path, @NonNull Closeable closeable) throws ClosedException {

        try {
            return Resolver.resolveNode(closeable, blocks, exchange, path);
        } catch (Throwable ignore) {
            // common exception to not resolve a a path
        }
        if (closeable.isClosed()) {
            throw new ClosedException();
        }
        return null;
    }

    @NonNull
    public String base32(@NonNull String pid) {
        /*

// Cast casts a buffer onto a multihash, and returns an error
// if it does not work.
func Cast(buf []byte) (Multihash, error) {
	dm, err := Decode(buf)
	if err != nil {
		return Multihash{}, err
	}

	if !ValidCode(dm.Code) {
		return Multihash{}, ErrUnknownCode
	}

	return Multihash(buf), nil
}

// ToCid encodes a peer ID as a CID of the public key.
//
// If the peer ID is invalid (e.g., empty), this will return the empty CID.
func ToCid(id ID) cid.Cid {
	m, err := mh.Cast([]byte(id))
	if err != nil {
		return cid.Cid{}
	}
	return cid.NewCidV1(cid.Libp2pKey, m)
}
         */
        try {

            /*
            id, err := peer.Decode(pid)
            if err != nil {
                return "", fmt.Errorf("invalid peer id")
            }
            return peer.ToCid(id).String(), nil*/
           return Stream.base32(host.getPeerId());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    public String getHost() {
        return base32(getPeerID());
    }
    public void publishName(@NonNull String cid, @NonNull Closeable closeable, int sequence)
            throws ClosedException {
        if (!isDaemonRunning()) {
            return;
        }
        try {

           Stream.PublishName(closeable, routing, privateKey, cid, sequence);
        } catch (Throwable ignore) {
            LogUtils.error(TAG, ignore);
        }

        if (closeable.isClosed()) {
            throw new ClosedException();
        }
    }

    public void dhtFindProviders(@NonNull String cid, int numProviders,
                                 @NonNull Providers providers) throws ClosedException {
        if (!isDaemonRunning()) {
            return;
        }

        if (numProviders < 1) {
            throw new RuntimeException("number of providers must be greater than 0");
        }

        try {
            routing.FindProvidersAsync(providers, Cid.Decode(cid), numProviders);
        } catch (Throwable ignore) {
        }
        if (providers.isClosed()) {
            throw new ClosedException();
        }
    }

    @Nullable
    public Multiaddr swarmPeer(@NonNull String pid) {
        if (!isDaemonRunning()) {
            return null;
        }
        try {
            return host.getNetwork().connect(PeerId.fromBase58(pid)).get().remoteAddress();
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }


    @NonNull
    public String resolve(@NonNull String root, @NonNull List<String> path,
                          @NonNull Closeable closeable) throws ClosedException {

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

            Stream.ResolveName(() -> /*(timeout.get() < System.currentTimeMillis())
                    ||*/ abort.get() || closeable.isClosed(), routing, new ResolveInfo() {

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
            }, decode(name), false, 8);

        } catch (ClosedException closedException){
            throw closedException;
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


    @NonNull
    public InputStream getInputStream(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {
        io.ipfs.utils.Reader reader = getReader(cid, closeable);
        return new ReaderStream(reader);
    }

    public boolean isDaemonRunning() {
        return running;
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
        return host.getNetwork().getConnections().size();
        //return node.numSwarmPeers();
    }

    /*
    @Override
    public void FindProvidersAsync(@NonNull io.dht.Providers providers, @NonNull Cid cid, int number) throws ClosedException {

        dhtFindProviders(cid.String(), number, providers);

    }

    @Override
    public void Provide(@NonNull Closeable closeable, @NonNull Cid cid) {
        try {
            dhtPublish(closeable, cid.String());
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }

    }*/

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

    public boolean isConnected(@NonNull String pid) {
        try {
            return host.getNetwork().connect(decode(pid)).get() != null;
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return false;
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
