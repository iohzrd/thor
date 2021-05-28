package io.ipfs.host;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import bitswap.pb.MessageOuterClass;
import identify.pb.IdentifyOuterClass;
import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.bitswap.BitSwap;
import io.ipfs.bitswap.BitSwapMessage;
import io.ipfs.bitswap.BitSwapReceiver;
import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.core.ConnectionIssue;
import io.ipfs.core.TimeoutCloseable;
import io.ipfs.crypto.PrivKey;
import io.ipfs.crypto.PubKey;
import io.ipfs.dht.KadDht;
import io.ipfs.dht.Routing;
import io.ipfs.exchange.Interface;
import io.ipfs.format.BlockStore;
import io.ipfs.ident.IdentityService;
import io.ipfs.ipns.Ipns;
import io.ipfs.multiaddr.Multiaddr;
import io.ipfs.multiaddr.Protocol;
import io.ipfs.push.Push;
import io.ipfs.relay.RelayService;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.util.SimpleTrustManagerFactory;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.AttributeKey;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.EmptyArrays;


public class LiteHost implements BitSwapReceiver {
    @NonNull
    public static final AttributeKey<PeerId> PEER_KEY = AttributeKey.newInstance("PEER_KEY");
    @NonNull
    public static final ConcurrentHashMap<PeerId, PubKey> remotes = new ConcurrentHashMap<>();
    @NonNull
    private static final String TAG = LiteHost.class.getSimpleName();
    @NonNull
    private static final Duration DefaultRecordEOL = Duration.ofHours(24);
    @NonNull
    private static final TrustManager tm = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String s) {
            try {
                if (IPFS.EVALUATE_PEER) {
                    for (X509Certificate cert : chain) {
                        PubKey pubKey = LiteHostCertificate.extractPublicKey(cert);
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
                if (IPFS.EVALUATE_PEER) {
                    for (X509Certificate cert : chain) {
                        PubKey pubKey = LiteHostCertificate.extractPublicKey(cert);
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
    private static int failure = 0;
    private static int success = 0;
    @NonNull
    public final List<ConnectionHandler> handlers = new ArrayList<>();
    @NonNull
    private final ConcurrentHashMap<PeerId, Long> tags = new ConcurrentHashMap<>();
    @NonNull
    private final ConcurrentSkipListSet<PeerId> relays = new ConcurrentSkipListSet<>();
    @NonNull
    private final ConcurrentHashMap<PeerId, Set<Multiaddr>> addressBook = new ConcurrentHashMap<>();
    @NonNull
    private final ConcurrentHashMap<PeerId, Connection> connections = new ConcurrentHashMap<>();
    @NonNull
    private final Routing routing;
    @NonNull
    private final PrivKey privKey;
    @NonNull
    private final Interface exchange;
    private final int port;
    @NonNull
    private final NioEventLoopGroup group = new NioEventLoopGroup(1);
    @NonNull
    private final LiteHostCertificate selfSignedCertificate;
    @NonNull
    private final Set<PeerId> swarm = ConcurrentHashMap.newKeySet();
    @Nullable
    private Push push;
    @Nullable
    private Channel server;
    private InetAddress localAddress;
    private Channel client;

    public LiteHost(@NonNull LiteHostCertificate selfSignedCertificate,
                    @NonNull PrivKey privKey,
                    @NonNull BlockStore blockstore,
                    int port, int alpha) {
        this.selfSignedCertificate = selfSignedCertificate;
        this.privKey = privKey;
        this.port = port;


        this.routing = new KadDht(this,
                new Ipns(), alpha, IPFS.KAD_DHT_BETA,
                IPFS.KAD_DHT_BUCKET_SIZE);

        this.exchange = BitSwap.create(this, blockstore);


        QuicSslContext sslClientContext = QuicSslContextBuilder.forClient(
                selfSignedCertificate.privateKey(), null, selfSignedCertificate.certificate()).
                trustManager(new SimpleTrustManagerFactory() {
                    @Override
                    protected void engineInit(KeyStore keyStore) {

                    }

                    @Override
                    protected void engineInit(ManagerFactoryParameters managerFactoryParameters) {

                    }

                    @Override
                    protected TrustManager[] engineGetTrustManagers() {
                        return new TrustManager[]{tm};
                    }
                }).
                applicationProtocols(IPFS.APRN).build();


        ChannelHandler codec = new QuicClientCodecBuilder()
                .sslContext(sslClientContext)
                .maxIdleTimeout(IPFS.GRACE_PERIOD, TimeUnit.SECONDS)
                .initialMaxData(IPFS.MESSAGE_SIZE_MAX)
                .initialMaxStreamDataBidirectionalLocal(IPFS.MESSAGE_SIZE_MAX)
                .initialMaxStreamDataBidirectionalRemote(IPFS.MESSAGE_SIZE_MAX)
                .initialMaxStreamsBidirectional(IPFS.HIGH_WATER)
                .initialMaxStreamsUnidirectional(IPFS.HIGH_WATER)
                .build();


        Bootstrap bs = new Bootstrap();

        try {
            client = bs.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(codec)
                    .bind(port).sync().channel();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }

    public void relays() {
        Set<String> addresses = new HashSet<>(IPFS.IPFS_RELAYS_NODES);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        for (String address : addresses) {
            try {
                Multiaddr multiaddr = new Multiaddr(address);
                String name = multiaddr.getStringComponent(Protocol.Type.P2P);
                Objects.requireNonNull(name);
                PeerId peerId = PeerId.fromBase58(name);
                if (!relays.contains(peerId)) {
                    Objects.requireNonNull(peerId);
                }
                executor.execute(() -> prepareRelay(peerId));
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }
    }

    public long getMaxTime() {
        return System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
    }

    private void prepareRelay(@NonNull PeerId relay) {
        try {
            LogUtils.debug(TAG, "Find Relay " + relay.toBase58());
            protectPeer(relay, getMaxTime());
            connectTo(new TimeoutCloseable(15), relay, IPFS.CONNECT_TIMEOUT);
            relays.add(relay);
            LogUtils.debug(TAG, "Found Relay " + relay.toBase58());
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    @NonNull
    public Routing getRouting() {
        return routing;
    }

    @NonNull
    public Interface getExchange() {
        return exchange;
    }

    @Override
    public boolean gatePeer(@NonNull PeerId peerID) {
        return exchange.gatePeer(peerID);
    }

    @Override
    public void receiveMessage(@NonNull PeerId peer, @NonNull BitSwapMessage incoming) {
        exchange.receiveMessage(peer, incoming);
    }

    public PeerId self() {
        return PeerId.fromPubKey(privKey.publicKey());
    }

    public void addConnectionHandler(@NonNull ConnectionHandler connectionHandler) {
        handlers.add(connectionHandler);
    }

    @NonNull
    public List<Connection> getConnections() {

        // first simple solution (test if conn is open)
        List<Connection> cones = new ArrayList<>();
        for (Connection conn : connections.values()) {
            if (conn.channel().isOpen()) {
                cones.add(conn);
            }
        }

        return cones;
    }

    public void forwardMessage(@NonNull PeerId peerId, @NonNull MessageLite msg) {
        if (msg instanceof MessageOuterClass.Message) {
            try {
                BitSwapMessage message = BitSwapMessage.newMessageFromProto(
                        (MessageOuterClass.Message) msg);
                receiveMessage(peerId, message);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable.getMessage());
            }
        }
    }

    public void findProviders(@NonNull Closeable closeable, @NonNull Routing.Providers providers,
                              @NonNull Cid cid) throws ClosedException {
        routing.findProviders(closeable, providers, cid);
    }

    public boolean hasAddresses(@NonNull PeerId peerId) {
        Collection<Multiaddr> addrInfo = addressBook.get(peerId);
        if (addrInfo != null) {
            return !addrInfo.isEmpty();
        }
        return false;
    }

    @NonNull
    public Set<Multiaddr> getAddresses(@NonNull PeerId peerId) {
        try {
            Collection<Multiaddr> addrInfo = addressBook.get(peerId);
            if (addrInfo != null) {
                return new HashSet<>(addrInfo);
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return Collections.emptySet();
    }

    @NonNull
    private List<Multiaddr> prepareAddresses(@NonNull PeerId peerId) {
        List<Multiaddr> all = new ArrayList<>();
        for (Multiaddr ma : getAddresses(peerId)) {
            try {
                if (ma.has(Protocol.Type.DNS6)) {
                    all.add(DnsResolver.resolveDns6(ma));
                } else if (ma.has(Protocol.Type.DNS4)) {
                    all.add(DnsResolver.resolveDns4(ma));
                } else if (ma.has(Protocol.Type.DNSADDR)) {
                    all.addAll(DnsResolver.resolveDnsAddress(ma));
                } else {
                    all.add(ma);
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable.getClass().getSimpleName());
            }
        }
        List<Multiaddr> result = new ArrayList<>();
        for (Multiaddr addr : all) {
            if (AddrInfo.isSupported(addr, true)) {
                result.add(addr);
            }
        }
        return result;
    }

    public void start() {


        QuicSslContext sslContext = QuicSslContextBuilder.forServer(
                selfSignedCertificate.privateKey(), null, selfSignedCertificate.certificate())
                .applicationProtocols(IPFS.APRN).clientAuth(ClientAuth.REQUIRE).
                        trustManager(new SimpleTrustManagerFactory() {
                            @Override
                            protected void engineInit(KeyStore keyStore) {

                            }

                            @Override
                            protected void engineInit(ManagerFactoryParameters managerFactoryParameters) {

                            }

                            @Override
                            protected TrustManager[] engineGetTrustManagers() {
                                return new TrustManager[]{tm};
                            }
                        }).build();


        ChannelHandler codec = new QuicServerCodecBuilder().sslContext(sslContext)

                .initialMaxData(IPFS.BLOCK_SIZE_LIMIT)
                .initialMaxStreamDataBidirectionalLocal(IPFS.BLOCK_SIZE_LIMIT)
                .initialMaxStreamDataBidirectionalRemote(IPFS.BLOCK_SIZE_LIMIT)
                .initialMaxStreamsBidirectional(IPFS.HIGH_WATER)
                .initialMaxStreamsUnidirectional(IPFS.HIGH_WATER)


                // Setup a token handler. In a production system you would want to implement and provide your custom
                // one.
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                // ChannelHandler that is added into QuicChannel pipeline.
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        // todo maybe
                    }

                    public void channelInactive(ChannelHandlerContext ctx) {
                        // todo maybe
                    }

                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                })
                .streamHandler(new WelcomeHandler(LiteHost.this)).build();
        try {
            Bootstrap bs = new Bootstrap();
            server = bs.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(codec)
                    .bind(new InetSocketAddress(NetUtil.LOCALHOST4, port)).sync().channel();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }

    public void connectTo(@NonNull Closeable closeable, @NonNull PeerId peerId, int timeout)
            throws ClosedException {

        try {
            connect(closeable, peerId, timeout);

        } catch (ConnectionIssue ignore) {

            AtomicBoolean done = new AtomicBoolean(false);
            routing.findPeer(() -> closeable.isClosed() || done.get(), peerId1 -> {
                try {
                    connect(closeable, peerId1, timeout);
                    done.set(true);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            }, peerId);
        }

    }

    public void PublishName(@NonNull Closeable closable,
                            @NonNull PrivKey privKey,
                            @NonNull String path,
                            @NonNull PeerId id, int sequence) throws ClosedException {


        Date eol = Date.from(new Date().toInstant().plus(DefaultRecordEOL));

        ipns.pb.Ipns.IpnsEntry
                record = Ipns.create(privKey, path.getBytes(), sequence, eol);

        PubKey pk = privKey.publicKey();

        record = Ipns.embedPublicKey(pk, record);

        byte[] bytes = record.toByteArray();

        byte[] ipns = IPFS.IPNS_PATH.getBytes();
        byte[] ipnsKey = Bytes.concat(ipns, id.getBytes());
        routing.putValue(closable, ipnsKey, bytes);
    }

    public boolean isConnected(@NonNull PeerId id) {
        try {
            return connections.get(id) != null;
        } catch (Throwable throwable) {
            return false;
        }
    }

    @NonNull
    public PeerInfo getPeerInfo(@NonNull Closeable closeable, @NonNull PeerId peerId)
            throws ClosedException, ConnectionIssue {

        Connection conn = connect(closeable, peerId, IPFS.CONNECT_TIMEOUT);

        return getPeerInfo(closeable, conn);
    }

    @NonNull
    public PeerInfo getPeerInfo(@NonNull Closeable closeable,
                                @NonNull Connection conn) throws ClosedException {
        return IdentityService.getPeerInfo(closeable, conn);
    }

    public void swarmReduce(@NonNull PeerId peerId) {
        swarm.remove(peerId);
    }

    public boolean addToAddressBook(@NonNull AddrInfo addrInfo) {
        boolean result = false;
        try {
            PeerId peerId = addrInfo.getPeerId();
            Set<Multiaddr> info = addressBook.get(peerId);

            if (addrInfo.hasAddresses()) {
                if (info != null) {
                    result = info.addAll(addrInfo.asSet());
                } else {
                    addressBook.put(peerId, addrInfo.asSet());
                    result = true;
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return result;
    }

    private void addConnection(@NonNull Connection connection) {


        connections.put(connection.remoteId(), connection);

        LogUtils.verbose(TAG, "Active Connections : " + connections.size());
        if (handlers.size() > 0) {
            ExecutorService executors = Executors.newFixedThreadPool(2);
            executors.execute(() -> {
                for (ConnectionHandler handle : handlers) {
                    try {
                        handle.handleConnection(connection);
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                }
            });
        }

    }

    public void swarmEnhance(@NonNull PeerId peerId) {
        swarm.add(peerId);
    }

    @NonNull
    public Set<PeerId> getPeers() {
        return new HashSet<>(swarm);
    }

    @Nullable
    public QuicStreamChannel getRelayStream(
            @NonNull Closeable closeable, @NonNull PeerId peerId) {

        for (PeerId relay : relays) {
            try {
                return getStream(closeable, relay, peerId);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }
        return null;
    }

    @Nullable
    private QuicStreamChannel getStream(@NonNull Closeable closeable, @NonNull PeerId relay,
                                        @NonNull PeerId peerId)
            throws ConnectionIssue, ClosedException {

        try {
            Connection conn = connect(closeable, relay, IPFS.CONNECT_TIMEOUT);

            return RelayService.getStream(conn, self(), peerId, IPFS.CONNECT_TIMEOUT);

        } catch (ClosedException | ConnectionIssue exception) {
            LogUtils.error(TAG, exception);
            throw exception;
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            throw new RuntimeException(throwable);
        }
    }

    public boolean canHop(@NonNull Closeable closeable, @NonNull PeerId peerId)
            throws ConnectionIssue, ClosedException {

        try {
            Connection conn = connect(closeable, peerId, IPFS.CONNECT_TIMEOUT);

            return RelayService.canHop(conn, IPFS.CONNECT_TIMEOUT);

        } catch (ClosedException | ConnectionIssue exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    // THIS is just a hack for a client (without running a server)
    @NonNull
    private InetAddress localAddress() throws IOException {
        if (localAddress == null) {
            synchronized (TAG.intern()) {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress("google.com", 80));
                localAddress = socket.getLocalAddress();
                socket.close();
            }
        }
        return localAddress;
    }

    // TODO should be improved with information of the device real
    // public IP, probably asks other devices for getting
    // the real IP address (relay, punch-hole, etc stuff
    @NonNull
    public List<Multiaddr> listenAddresses() {
        try {
            // TODO the listen address does not contain real IP address

            List<Multiaddr> list = new ArrayList<>();
            if (server != null) {
                list.add(transform(server.localAddress()));
            } else {

                InetAddress inetAddress = localAddress();
                String pre = "/ip4/";
                if (inetAddress instanceof Inet6Address) {
                    pre = "/ip6";
                }

                list.add(new Multiaddr(pre +
                        localAddress.getHostAddress() + "/udp/" + port + "/quic"));
            }

            return list;
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return Collections.emptyList();

    }

    // TODO improve (check network configuration or so)
    private boolean inet6() {
        if (localAddress != null) {
            return localAddress instanceof Inet6Address;
        }
        return false;
    }

    @NonNull
    public Connection connect(@NonNull Closeable closeable, @NonNull PeerId peerId, int timeout)
            throws ConnectionIssue, ClosedException {


        synchronized (peerId.toBase58().intern()) {

            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            Connection connection = connections.get(peerId);
            if (connection != null) {
                return connection;
            }

            boolean ipv6 = inet6();
            List<Multiaddr> addrInfo = prepareAddresses(peerId);

            if (addrInfo.isEmpty()) {
                failure++;
                LogUtils.debug(TAG, "Run false" + " Success " + success + " " +
                        "Failure " + failure + " " + "/p2p/" + peerId.toBase58() + " " +
                        "No address");
                throw new ConnectionIssue();
            }

            for (Multiaddr address : addrInfo) {

                if (ipv6 && !address.has(Protocol.Type.IP6)) {
                    continue;
                }
                if (!ipv6 && address.has(Protocol.Type.IP6)) {
                    continue;
                }
                if (closeable.isClosed()) {
                    throw new ClosedException();
                }
                long start = System.currentTimeMillis();
                boolean run = false;
                try {
                    Promise<QuicChannel> future = dial(address, peerId);


                    QuicChannel quic = future.get(timeout, TimeUnit.SECONDS);
                    Objects.requireNonNull(quic);

                    Connection conn = new LiteConnection(quic, transform(quic.remoteAddress()));
                    quic.closeFuture().addListener(future1 -> removeConnection(conn));
                    addConnection(conn);
                    run = true;
                    return conn;
                } catch (Throwable ignore) {
                    // nothing to do here
                } finally {
                    if (run) {
                        success++;
                    } else {
                        failure++;
                    }

                    LogUtils.debug(TAG, "Run " + run + " Success " + success + " " +
                            "Failure " + failure + " " +
                            address + "/p2p/" + peerId.toBase58() + " " +
                            (System.currentTimeMillis() - start));

                }

            }

            throw new ConnectionIssue();
        }
    }

    private void removeConnection(@NonNull Connection conn) {
        try {
            LogUtils.verbose(TAG, "Remove Connection " + conn.remoteId().toBase58());

            connections.remove(conn.remoteId());

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        } finally {
            LogUtils.verbose(TAG, "Update Active Connections " + connections.size());
        }
    }

    public void push(@NonNull PeerId peerId, @NonNull byte[] content) {
        try {
            Objects.requireNonNull(peerId);
            Objects.requireNonNull(content);

            if (push != null) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.submit(() -> push.push(peerId, new String(content)));
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }

    public void setPush(@Nullable Push push) {
        this.push = push;
    }


    @NonNull
    private Multiaddr transform(@NonNull SocketAddress socketAddress) {

        InetSocketAddress inetSocketAddress = (InetSocketAddress) socketAddress;
        InetAddress inetAddress = inetSocketAddress.getAddress();
        boolean ipv6 = false;
        if (inetAddress instanceof Inet6Address) {
            ipv6 = true;
        }
        int port = inetSocketAddress.getPort();
        String multiaddress = "";
        if (ipv6) {
            multiaddress = multiaddress.concat("/ip6/");
        } else {
            multiaddress = multiaddress.concat("/ip4/");
        }
        multiaddress = multiaddress + inetAddress.getHostAddress() + "/udp/" + port + "/quic";
        return new Multiaddr(multiaddress);

    }

    public IdentifyOuterClass.Identify createIdentity(@Nullable SocketAddress socketAddress) {

        IdentifyOuterClass.Identify.Builder builder = IdentifyOuterClass.Identify.newBuilder()
                .setAgentVersion(IPFS.AGENT)
                .setPublicKey(ByteString.copyFrom(privKey.publicKey().bytes()))
                .setProtocolVersion(IPFS.PROTOCOL_VERSION);

        List<Multiaddr> addresses = listenAddresses();
        for (Multiaddr addr : addresses) {
            builder.addListenAddrs(ByteString.copyFrom(addr.getBytes()));
        }

        List<String> protocols = getProtocols();
        for (String protocol : protocols) {
            builder.addProtocols(protocol);
        }

        try {
            if (socketAddress != null) {
                Multiaddr observed = transform(socketAddress);
                builder.setObservedAddr(ByteString.copyFrom(observed.getBytes()));
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return builder.build();
    }


    private List<String> getProtocols() {
        return Arrays.asList(IPFS.STREAM_PROTOCOL, IPFS.IDENTITY_PROTOCOL, IPFS.BITSWAP_PROTOCOL);
    }


    public void shutdown() {
        try {
            if (server != null) {
                server.closeFuture().sync();
            }
            group.shutdownGracefully();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        } finally {
            server = null;
        }
    }


    public void protectPeer(@NonNull PeerId peerId, long time) {
        tags.put(peerId, time);
    }

    public boolean isProtected(@NonNull PeerId peerId) {
        Long time = tags.get(peerId);
        if (time != null) {
            return time > System.currentTimeMillis();
        }
        return false;
    }

    public int numConnections() {
        return getConnections().size();
    }


    public Promise<QuicChannel> dial(@NonNull Multiaddr multiaddr,
                                     @NonNull PeerId peerId) throws UnknownHostException {

        InetAddress inetAddress;
        if (multiaddr.has(Protocol.Type.IP4)) {
            inetAddress = Inet4Address.getByName(multiaddr.getStringComponent(Protocol.Type.IP4));
        } else if (multiaddr.has(Protocol.Type.IP6)) {
            inetAddress = Inet6Address.getByName(multiaddr.getStringComponent(Protocol.Type.IP6));
        } else {
            throw new RuntimeException();
        }
        int port = multiaddr.getPort();


        return (Promise<QuicChannel>) QuicChannel.newBootstrap(client)
                .attr(PEER_KEY, peerId)
                .streamHandler(new WelcomeHandler(LiteHost.this))
                .remoteAddress(new InetSocketAddress(inetAddress, port))
                .connect();


    }

    public boolean swarmContains(@NonNull PeerId peerId) {
        return swarm.contains(peerId);
    }

    public List<PeerId> getRelays() {
        return new ArrayList<>(relays);
    }

    public long getShortTime() {
        return System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
    }

    public void disconnect(@NonNull PeerId peerId) {
        Connection connection = connections.get(peerId);
        if (connection != null) {
            connection.disconnect();
        }

    }

    public class LiteConnection implements Connection {
        private final QuicChannel channel;
        private final Multiaddr multiaddr;

        public LiteConnection(@NonNull QuicChannel channel, @NonNull Multiaddr multiaddr) {
            this.channel = channel;
            this.multiaddr = multiaddr;
        }

        @Override
        public Multiaddr remoteAddress() {
            return multiaddr;
        }

        @Override
        public QuicChannel channel() {
            return channel;
        }

        @Override
        public void disconnect() {
            try {
                if (!isProtected(remoteId())) {
                    channel.disconnect();
                }
            } catch (Exception exception) {
                LogUtils.error(TAG, exception);
            }
        }


        @Override
        public PeerId remoteId() {
            return channel.attr(LiteHost.PEER_KEY).get();
        }


    }
}
