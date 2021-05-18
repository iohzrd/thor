package io.ipfs.host;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

import java.io.ByteArrayOutputStream;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import bitswap.pb.MessageOuterClass;
import dht.pb.Dht;
import identify.pb.IdentifyOuterClass;
import io.LogUtils;
import io.core.Closeable;
import io.core.ClosedException;
import io.core.ConnectionIssue;
import io.core.ProtocolIssue;
import io.core.TimeoutIssue;
import io.crypto.PrivKey;
import io.crypto.PubKey;
import io.ipfs.IPFS;
import io.ipfs.bitswap.BitSwap;
import io.ipfs.bitswap.BitSwapMessage;
import io.ipfs.bitswap.BitSwapNetwork;
import io.ipfs.bitswap.BitSwapReceiver;
import io.ipfs.cid.Cid;
import io.ipfs.dht.KadDHT;
import io.ipfs.dht.Routing;
import io.ipfs.exchange.Interface;
import io.ipfs.format.BlockStore;
import io.ipfs.multiaddr.Multiaddr;
import io.ipfs.multiaddr.Protocol;
import io.ipns.Ipns;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.util.SimpleTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamPriority;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.incubator.codec.quic.QuicheQuicConnectionStats;
import io.netty.util.AttributeKey;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.EmptyArrays;
import io.quic.QuicheWrapper;


public class LiteHost implements BitSwapReceiver, BitSwapNetwork, Metrics {
    @NonNull
    public static final AttributeKey<PeerId> PEER_KEY = AttributeKey.newInstance("PEER_KEY");
    private static final String TAG = LiteHost.class.getSimpleName();
    @NonNull
    public static final ConcurrentHashMap<PeerId, PubKey> remotes = new ConcurrentHashMap<>();
    @NonNull
    private static final Duration DefaultRecordEOL = Duration.ofHours(24);
    @NonNull
    private static final TrustManager tm = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String s) {
            try {
                if (IPFS.EVALUATE_PEER) {
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
                if (IPFS.EVALUATE_PEER) {
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
    private final Set<PeerId> tags = ConcurrentHashMap.newKeySet();
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
    private final LiteSignedCertificate selfSignedCertificate;
    @NonNull
    public List<ConnectionHandler> handlers = new ArrayList<>();
    @Nullable
    private Pusher pusher;
    @Nullable
    private Channel server;
    @NonNull
    private final QuicSslContext sslClientContext;
    @NonNull
    private final Set<PeerId> swarm = ConcurrentHashMap.newKeySet();

    @NonNull
    public ConcurrentHashMap<QuicChannel, QuicStreamChannel> kads = new ConcurrentHashMap<>();
    @NonNull
    public ConcurrentHashMap<QuicChannel, QuicStreamChannel> bitSwaps = new ConcurrentHashMap<>();
    @NonNull
    public ConcurrentHashMap<QuicChannel, QuicStreamChannel> pushes = new ConcurrentHashMap<>();
    @NonNull
    public ConcurrentHashMap<QuicChannel, QuicStreamChannel> relays = new ConcurrentHashMap<>();
    @NonNull
    public ConcurrentHashMap<QuicChannel, QuicStreamChannel> idents = new ConcurrentHashMap<>();


    private InetAddress localAddress;

    public LiteHost(@NonNull LiteSignedCertificate selfSignedCertificate, @NonNull PrivKey privKey,
                    @NonNull BlockStore blockstore, int port, int alpha) {
        this.selfSignedCertificate = selfSignedCertificate;
        this.privKey = privKey;
        this.port = port;


        this.routing = new KadDHT(this,
                new Ipns(), alpha, IPFS.KAD_DHT_BETA,
                IPFS.KAD_DHT_BUCKET_SIZE);

        this.exchange = BitSwap.create(this, blockstore);


        sslClientContext = QuicSslContextBuilder.forClient(
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
    public boolean GatePeer(@NonNull PeerId peerID) {
        return exchange.GatePeer(peerID);
    }

    @Override
    public boolean ReceiveMessage(@NonNull PeerId peer, @NonNull BitSwapMessage incoming) {
        return exchange.ReceiveMessage(peer, incoming);
    }


    @Override
    public PeerId Self() {
        return PeerId.fromPubKey(privKey.publicKey());
    }

    public void addConnectionHandler(@NonNull ConnectionHandler connectionHandler) {
        handlers.add(connectionHandler);
    }

    @NonNull
    public List<Connection> getConnections() {

        // first simple solution (testi is conn is open
        List<Connection> conns = new ArrayList<>();
        for (Connection conn : connections.values()) {
            if (conn.channel().isOpen()) {
                conns.add(conn);
            }
        }

        return conns;
    }

    public boolean forwardMessage(@NonNull PeerId peerId, @NonNull MessageLite msg) {
        if (msg instanceof MessageOuterClass.Message) {
            try {
                BitSwapMessage message = BitSwapMessage.newMessageFromProto(
                        (MessageOuterClass.Message) msg);
                return ReceiveMessage(peerId, message);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable.getMessage());
            }
        }
        return false;
    }

    @Override
    public void findProviders(@NonNull Closeable closeable, @NonNull Routing.Providers providers,
                              @NonNull Cid cid) throws ClosedException {
        routing.FindProviders(closeable, providers, cid);
    }

    @NonNull
    public Set<Multiaddr> getAddresses(@NonNull PeerId peerId) {
        Set<Multiaddr> all = new HashSet<>();
        try {
            Collection<Multiaddr> addrInfo = addressBook.get(peerId);
            if (addrInfo != null) {
                all.addAll(addrInfo);
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return all;
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
                LogUtils.verbose(TAG, throwable.getClass().getSimpleName());
            }
        }
        return all;
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

    @Override
    public void connectTo(@NonNull Closeable closeable, @NonNull PeerId peerId, int timeout)
            throws ClosedException, ConnectionIssue {
         connect(closeable, peerId, timeout);

    }

    public void PublishName(@NonNull Closeable closable,
                            @NonNull PrivKey privKey,
                            @NonNull String path,
                            @NonNull PeerId id, int sequence) throws ClosedException {


        Date eol = Date.from(new Date().toInstant().plus(DefaultRecordEOL));

        ipns.pb.Ipns.IpnsEntry
                record = Ipns.Create(privKey, path.getBytes(), sequence, eol);

        PubKey pk = privKey.publicKey();

        record = Ipns.EmbedPublicKey(pk, record);

        byte[] bytes = record.toByteArray();

        byte[] ipns = IPFS.IPNS_PATH.getBytes();
        byte[] ipnsKey = Bytes.concat(ipns, id.getBytes());
        routing.PutValue(closable, ipnsKey, bytes);
    }

    public boolean isConnected(@NonNull PeerId id) {
        try {
            return connections.get(id) != null;
        } catch (Throwable throwable) {
            return false;
        }
    }

    @Override
    public void writeMessage(@NonNull Closeable closeable, @NonNull PeerId peerId,
                             @NonNull BitSwapMessage message, short priority)
            throws ClosedException, ProtocolIssue, TimeoutIssue, ConnectionIssue {

        try {
            Connection conn = connect(closeable, peerId, IPFS.CONNECT_TIMEOUT);

            /*
            QuicStreamChannel stream = getStream(closeable, IPFS.BITSWAP_PROTOCOL, conn, priority);
            stream.writeAndFlush(DataHandler.encode(message.ToProtoV1()));
            */


            send(closeable, IPFS.BIT_SWAP_PROTOCOL, conn,
                    message.ToProtoV1().toByteArray(), priority);

        } catch (ClosedException | ConnectionIssue exception) {
            throw exception;
        } catch (Throwable throwable) {
            Throwable cause = throwable.getCause();
            if (cause != null) {
                if (cause instanceof ProtocolIssue) {
                    throw new ProtocolIssue();
                }
                if (cause instanceof ConnectionIssue) {
                    throw new ConnectionIssue();
                }
                if (cause instanceof ReadTimeoutException) {
                    throw new TimeoutIssue();
                }
            }
            throw new RuntimeException(throwable);
        }

    }

    @NonNull
    public PeerInfo getPeerInfo(@NonNull Closeable closeable,
                                @NonNull Connection conn) throws ClosedException {

        try {
            MessageLite message = request(closeable, IPFS.IDENTITY_PROTOCOL, conn, null,
                    IPFS.PRIORITY_HIGH);
            Objects.requireNonNull(message);

            IdentifyOuterClass.Identify identify = (IdentifyOuterClass.Identify) message;
            Objects.requireNonNull(identify);

            String agent = identify.getAgentVersion();
            Multiaddr observedAddr = null;
            if (identify.hasObservedAddr()) {
                observedAddr = new Multiaddr(identify.getObservedAddr().toByteArray());
            }

            return new PeerInfo(conn.remoteId(), agent, conn.remoteAddress(), observedAddr);
        } catch (ClosedException closedException) {
            throw closedException;
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public void swarmReduce(@NonNull PeerId peerId) {
        swarm.remove(peerId);
    }

    public void addAddrs(@NonNull AddrInfo addrInfo) {

        try {
            PeerId peerId = addrInfo.getPeerId();
            Set<Multiaddr> info = addressBook.get(peerId);

            if (addrInfo.hasAddresses()) {
                if (info != null) {
                    info.addAll(addrInfo.asSet());
                } else {
                    addressBook.put(peerId, addrInfo.asSet());
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
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
    @Override
    public Set<PeerId> getPeers() {

        Set<PeerId> peerIds = new HashSet<>(swarm);

        /* TODO
        for (Connection connection : getConnections()) {
            peerIds.add(connection.remoteId());
        }*/

        return peerIds;
    }

    public boolean canHop(@NonNull Closeable closeable, @NonNull PeerId peerId)
            throws ConnectionIssue, ClosedException {


        relay.pb.Relay.CircuitRelay message = relay.pb.Relay.CircuitRelay.newBuilder()
                .setType(relay.pb.Relay.CircuitRelay.Type.CAN_HOP)
                .build();

        try {
            Connection conn = connect(closeable, peerId, IPFS.CONNECT_TIMEOUT);
            MessageLite messageLite = request(closeable, IPFS.RELAY_PROTOCOL, conn, message,
                    IPFS.PRIORITY_URGENT);
            Objects.requireNonNull(messageLite);
            relay.pb.Relay.CircuitRelay msg = (relay.pb.Relay.CircuitRelay) messageLite;
            Objects.requireNonNull(msg);
            return msg.getType() == relay.pb.Relay.CircuitRelay.Type.STATUS;

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
                if(inetAddress instanceof  Inet6Address){
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

    @NonNull
    public Connection connect(@NonNull Closeable closeable, @NonNull PeerId peerId, int timeout)
            throws ConnectionIssue, ClosedException {


        Connection connection = connections.get(peerId);
        if (connection != null) {
            return connection;
        }

        List<Multiaddr> addrInfo = prepareAddresses(peerId);

        if (!addrInfo.isEmpty()) {

            for (Multiaddr address : addrInfo) {

                if (closeable.isClosed()) {
                    throw new ClosedException();
                }

                try {
                    long start = System.currentTimeMillis();
                    Promise<QuicChannel> future = dial(address, peerId);


                    QuicChannel quic = future.get(timeout, TimeUnit.SECONDS);
                    Objects.requireNonNull(quic);
                    LogUtils.verbose(TAG, "Success " + address + " " + (System.currentTimeMillis() - start));

                    Connection conn = new LiteConnection(quic, transform(quic.remoteAddress()));
                    quic.closeFuture().addListener(future1 -> removeConnection(conn));
                    addConnection(conn);

                    return conn;
                } catch (Throwable throwable) {
                    LogUtils.debug(TAG, address + " " +
                            throwable.getClass().getSimpleName());
                }
            }
        }
        throw new ConnectionIssue();

    }

    private void removeConnection(@NonNull Connection conn) {
        try {
            LogUtils.verbose(TAG, "Remove Connection " + conn.remoteId().toBase58());
            connections.remove(conn.remoteId());

            conn.channel().parent().close().get();

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

            if (pusher != null) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.submit(() -> pusher.push(peerId, new String(content)));
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }

    // TODO evaluate if it makes sens
    public int evalMaxResponseLength(@NonNull String protocol) {
        if (protocol.equals(IPFS.IDENTITY_PROTOCOL) ||
                protocol.equals(IPFS.KAD_DHT_PROTOCOL)) {
            return 25000;
        }
        return IPFS.BLOCK_SIZE_LIMIT;
    }

    public QuicStreamChannel getStream(@NonNull Closeable closeable,
                                       @NonNull String protocol,
                                       @NonNull Connection conn, short priority)
            throws InterruptedException, ExecutionException, ClosedException {

        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        QuicChannel quicChannel = conn.channel();

        QuicStreamChannel stored = getStream(quicChannel, protocol);
        if (stored != null) {
            if (stored.isOpen() && stored.isWritable()) {
                return stored;
            } else {
                removeStream(quicChannel, protocol);
            }
        }


        CompletableFuture<QuicStreamChannel> ctrl = getStream(quicChannel, protocol, priority);


        while (!ctrl.isDone()) {
            if (closeable.isClosed()) {
                ctrl.cancel(true);
            }
        }

        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        QuicStreamChannel stream = ctrl.get();

        Objects.requireNonNull(stream);
        putStream(quicChannel, protocol, stream);
        return stream;
    }

    public void setPusher(@Nullable Pusher pusher) {
        this.pusher = pusher;
    }

    public void send(@NonNull Closeable closeable, @NonNull String protocol,
                     @NonNull Connection conn, @NonNull byte[] data, short priority)
            throws InterruptedException, ExecutionException, ClosedException {

        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        long time = System.currentTimeMillis();

        /*
        QuicChannel quicChannel = conn.channel();
        CompletableFuture<Void> cf = send(quicChannel, protocol, data, priority);

        while (!cf.isDone()) {
            if (closeable.isClosed()) {
                cf.cancel(true);
            }
        }

        if (closeable.isClosed()) {
            throw new ClosedException();
        }
        cf.get();*/

        QuicStreamChannel stream = getStream(closeable, protocol, conn, priority);
        stream.writeAndFlush(DataHandler.encode(data));


        LogUtils.verbose(TAG, "Send took " + protocol + " " + (System.currentTimeMillis() - time));

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

    IdentifyOuterClass.Identify createIdentity(@NonNull SocketAddress socketAddress) {

        IdentifyOuterClass.Identify.Builder builder = IdentifyOuterClass.Identify.newBuilder()
                .setAgentVersion(IPFS.AGENT)
                .setPublicKey(ByteString.copyFrom(privKey.publicKey().bytes()))
                .setProtocolVersion(IPFS.PROTOCOL_VERSION);

        List<Multiaddr> multiaddrs = listenAddresses();
        for (Multiaddr addr : multiaddrs) {
            builder.addListenAddrs(ByteString.copyFrom(addr.getBytes()));
        }

        List<String> protocols = getProtocols();
        for (String protocol : protocols) {
            builder.addProtocols(protocol);
        }
        try {
            Multiaddr observed = transform(socketAddress);
            builder.setObservedAddr(ByteString.copyFrom(observed.getBytes()));
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return builder.build();
    }


    private List<String> getProtocols() {
        return Arrays.asList(IPFS.STREAM_PROTOCOL, IPFS.IDENTITY_PROTOCOL, IPFS.BIT_SWAP_PROTOCOL);
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


    public long getLatency(@NonNull PeerId peerId) {
        Connection conn = connections.get(peerId);
        if (conn != null) {
            QuicheQuicConnectionStats stats = QuicheWrapper.getStats(
                    conn.channel().connection());
            if (stats != null) {
                return stats.deliveryRate();
            }
        }
        return Long.MAX_VALUE;
    }

    public void protectPeer(@NonNull PeerId peerId) {
        tags.add(peerId);
    }

    public void unprotectPeer(@NonNull PeerId peerId) {
        tags.remove(peerId);
    }

    public boolean isProtected(@NonNull PeerId peerId) {
        return tags.contains(peerId);
    }

    public void trimConnections() {

        int numConns = numConnections();
        LogUtils.verbose(TAG, "trimConnections (before) " + numConns);

        int highWater = IPFS.HIGH_WATER;
        if (numConns > highWater) {

            int lowWater = IPFS.LOW_WATER;
            int hasToBeClosed = numConns - lowWater;

            // TODO maybe sort connections how fast they are (the fastest will not be closed)

            for (Connection connection : getConnections()) {

                try {
                    QuicheQuicConnectionStats stats = QuicheWrapper.getStats(
                            connection.channel().connection());
                    if (stats != null) {
                        LogUtils.verbose(TAG, "trimConnections (stats) " + stats.toString());
                    }

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }


                if (hasToBeClosed > 0) {
                    try {
                        PeerId peerId = connection.remoteId();
                        if (!isProtected(peerId)) {

                            connection.close().get();
                            hasToBeClosed--;
                        }
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                }
            }
        }
        LogUtils.verbose(TAG, "trimConnections (after) " + numConnections());
    }


    public int numConnections() {
        return getConnections().size();
    }

    @NonNull
    public MessageLite request(@NonNull Closeable closeable, @NonNull String protocol,
                               @NonNull Connection conn, @Nullable MessageLite message,
                               short priority)
            throws InterruptedException, ExecutionException, ClosedException {


        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        QuicChannel quicChannel = conn.channel();
        long time = System.currentTimeMillis();
        /*
        CompletableFuture<MessageLite> request = new CompletableFuture<>();
        QuicStreamChannel stream = getStream(closeable, protocol, conn, priority);
        stream.pipeline().removeLast();
        stream.pipeline().addLast(new ProtocolHandler(this, request, protocol));

        if( message != null ) {
            stream.writeAndFlush(DataHandler.encode(message));
        }*/

        CompletableFuture<MessageLite> request = request(quicChannel, protocol, message, priority);

        while (!request.isDone()) {
            if (closeable.isClosed()) {
                request.cancel(true);
            }
        }

        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        MessageLite res = request.get();
        LogUtils.info(TAG, "Request took " + protocol + " " + (System.currentTimeMillis() - time));
        Objects.requireNonNull(res);
        return res;
    }

    public CompletableFuture<QuicStreamChannel> getStream(@NonNull QuicChannel quicChannel,
                                                          @NonNull String protocol, short priority) {


        CompletableFuture<QuicStreamChannel> stream = new CompletableFuture<>();

        try {
            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new NegotiatorHandler(stream, this, protocol)).sync().get();

            streamChannel.updatePriority(new QuicStreamPriority(priority, false));


            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
            streamChannel.writeAndFlush(DataHandler.writeToken(protocol));

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            stream.completeExceptionally(throwable);
        }

        return stream;
    }

    public CompletableFuture<Void> send(@NonNull QuicChannel quicChannel,
                                        @NonNull String protocol,
                                        @NonNull byte[] message,
                                        short priority) {


        CompletableFuture<Void> ret = new CompletableFuture<>();

        try {
            /*
            QuicheWrapper.QuicheStream streamChannel = QuicheWrapper.createStream(
                    new TimeoutCloseable(closeable, 1),
                    QuicStreamType.BIDIRECTIONAL,
                    new QuicheWrapper.QuicheStreamStreamHandler(){
                        private DataHandler reader = new DataHandler(IPFS.BLOCK_SIZE_LIMIT);

                        @Override
                        public void exceptionCaught(QuicheWrapper.QuicheStream ctx, Throwable cause) {

                        }

                        public void channelRead0(QuicheWrapper.QuicheStream ctx, ByteBuf msg)
                                throws Exception {


                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            msg.readBytes(out, msg.readableBytes());
                            byte[] data = out.toByteArray();
                            reader.load(data);

                            if (reader.isDone()) {
                                for (String received : reader.getTokens()) {
                                    LogUtils.error(TAG, "send " + received);
                                    if (Objects.equals(received, IPFS.NA)) {
                                        throw new ProtocolIssue();
                                    } else if (Objects.equals(received, IPFS.STREAM_PROTOCOL)) {
                                    } else if (Objects.equals(received, protocol)) {
                                        activation.complete(null);
                                    } else {
                                        throw new ProtocolIssue();
                                    }
                                }
                                reader = new DataHandler(IPFS.BLOCK_SIZE_LIMIT);
                            } else {
                                LogUtils.debug(TAG, "iteration " + data.length + " "
                                        + reader.expectedBytes() + " " + protocol );
                            }
                        }
                    }, quicChannel.getIdGenerator(), quicChannel.connection());

            */
            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new SimpleChannelInboundHandler<Object>() {
                        private DataHandler reader = new DataHandler(IPFS.BLOCK_SIZE_LIMIT);

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                throws Exception {
                            LogUtils.error(TAG, protocol + " " + cause);
                            ret.completeExceptionally(cause);
                            ctx.close().get();
                        }

                        @Override
                        public void channelUnregistered(ChannelHandlerContext ctx) {
                            ret.completeExceptionally(new ConnectionIssue());
                        }

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, Object value)
                                throws Exception {


                            ByteBuf msg = (ByteBuf) value;
                            Objects.requireNonNull(msg);

                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            msg.readBytes(out, msg.readableBytes());
                            byte[] data = out.toByteArray();
                            reader.load(data);

                            if (reader.isDone()) {
                                for (String received : reader.getTokens()) {
                                    LogUtils.debug(TAG, "send " + received);
                                    if (Objects.equals(received, IPFS.NA)) {
                                        throw new ProtocolIssue();
                                    } else if (Objects.equals(received, IPFS.STREAM_PROTOCOL)) {
                                    } else if (Objects.equals(received, protocol)) {
                                        ret.complete(ctx.writeAndFlush(
                                                DataHandler.encode(message)).addListener(
                                                (ChannelFutureListener) future -> ctx.close()).get());
                                    } else {
                                        throw new ProtocolIssue();
                                    }
                                }


                                reader = new DataHandler(IPFS.BLOCK_SIZE_LIMIT);
                            } else {
                                LogUtils.debug(TAG, "iteration " + data.length + " "
                                        + reader.expectedBytes() + " " + protocol + " " + ctx.name() + " "
                                        + ctx.channel().remoteAddress());
                            }
                        }
                    }).sync().get();

            //streamChannel.pipeline().addFirst(new ReadTimeoutHandler(5, TimeUnit.SECONDS));


            streamChannel.updatePriority(new QuicStreamPriority(priority, false));


            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
            streamChannel.writeAndFlush(DataHandler.writeToken(protocol));


        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            ret.completeExceptionally(throwable);
        }

        return ret;
    }

    public CompletableFuture<MessageLite> request(@NonNull QuicChannel quicChannel,
                                                  @NonNull String protocol,
                                                  @Nullable MessageLite messageLite,
                                                  short priority) {

        CompletableFuture<MessageLite> request = new CompletableFuture<>();
        CompletableFuture<Void> activation = new CompletableFuture<>();

        try {
            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new SimpleChannelInboundHandler<Object>() {

                        private DataHandler reader = new DataHandler(IPFS.BLOCK_SIZE_LIMIT);


                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                throws Exception {
                            LogUtils.error(TAG, protocol + " " + cause);
                            request.completeExceptionally(cause);
                            activation.completeExceptionally(cause);
                            ctx.close().get();
                        }

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, Object value)
                                throws Exception {


                            ByteBuf msg = (ByteBuf) value;
                            Objects.requireNonNull(msg);

                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            msg.readBytes(out, msg.readableBytes());
                            byte[] data = out.toByteArray();
                            reader.load(data);

                            if (reader.isDone()) {

                                for (String received : reader.getTokens()) {
                                    LogUtils.verbose(TAG, "request " + received);
                                    if (Objects.equals(received, IPFS.NA)) {
                                        throw new ProtocolIssue();
                                    } else if (Objects.equals(received, IPFS.STREAM_PROTOCOL)) {
                                    } else if (Objects.equals(received, protocol)) {

                                        if (messageLite != null) {
                                            activation.complete(
                                                    ctx.writeAndFlush(DataHandler.encode(messageLite))
                                                            .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).get());
                                        } else {
                                            activation.complete(null);
                                        }
                                    } else {
                                        throw new ProtocolIssue();
                                    }
                                }

                                byte[] message = reader.getMessage();

                                if (message != null) {
                                    switch (protocol) {
                                        case IPFS.RELAY_PROTOCOL:
                                            LogUtils.debug(TAG, "Found " + protocol);
                                            request.complete(relay.pb.Relay.CircuitRelay.parseFrom(message));
                                            break;
                                        case IPFS.IDENTITY_PROTOCOL:
                                            LogUtils.debug(TAG, "Found " + protocol);
                                            request.complete(IdentifyOuterClass.Identify.parseFrom(message));
                                            break;
                                        case IPFS.BIT_SWAP_PROTOCOL:
                                            LogUtils.debug(TAG, "Found " + protocol);
                                            request.complete(MessageOuterClass.Message.parseFrom(message));
                                            break;
                                        case IPFS.KAD_DHT_PROTOCOL:
                                            LogUtils.debug(TAG, "Found " + protocol);
                                            request.complete(Dht.Message.parseFrom(message));
                                            break;
                                        default:
                                            throw new Exception("unknown protocol");
                                    }
                                    ctx.close();
                                }
                                reader = new DataHandler(evalMaxResponseLength(protocol));
                            } else {
                                LogUtils.debug(TAG, "iteration " + reader.hasRead() + " "
                                        + reader.expectedBytes() + " " + protocol + " " + ctx.name() + " "
                                        + ctx.channel().remoteAddress());
                            }
                        }
                    }).sync().get();


            //streamChannel.pipeline().addFirst(new ReadTimeoutHandler(5, TimeUnit.SECONDS));

            streamChannel.updatePriority(new QuicStreamPriority(priority, false));


            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
            streamChannel.writeAndFlush(DataHandler.writeToken(protocol));

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            activation.completeExceptionally(throwable);
            request.completeExceptionally(throwable);
        }

        return activation.thenCompose(s -> request);
    }

    public Promise<QuicChannel> dial(@NonNull Multiaddr multiaddr,
                                     @NonNull PeerId peerId) throws UnknownHostException, InterruptedException {

        InetAddress inetAddress;
        if (multiaddr.has(Protocol.Type.IP4)) {
            inetAddress = Inet4Address.getByName(multiaddr.getStringComponent(Protocol.Type.IP4));
        } else if (multiaddr.has(Protocol.Type.IP6)) {
            inetAddress = Inet6Address.getByName(multiaddr.getStringComponent(Protocol.Type.IP6));
        } else {
            throw new RuntimeException();
        }
        int port = multiaddr.getPort();

        ChannelHandler codec = new QuicClientCodecBuilder()
                .sslContext(sslClientContext)
                .maxIdleTimeout(IPFS.GRACE_PERIOD, TimeUnit.SECONDS)
                .initialMaxData(IPFS.BLOCK_SIZE_LIMIT)
                .initialMaxStreamDataBidirectionalLocal(IPFS.BLOCK_SIZE_LIMIT)
                .initialMaxStreamDataBidirectionalRemote(IPFS.BLOCK_SIZE_LIMIT)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                .build();


        Bootstrap bs = new Bootstrap();

        Channel client = bs.group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec)
                .bind(0).sync().channel();


        return (Promise<QuicChannel>) QuicChannel.newBootstrap(client)
                .attr(PEER_KEY, peerId)
                .streamHandler(new WelcomeHandler(LiteHost.this))
                .remoteAddress(new InetSocketAddress(inetAddress, port))
                .connect();


    }

    public QuicStreamChannel getStream(@NonNull QuicChannel quicChannel, @NonNull String protocol) {
        switch (protocol) {
            case IPFS.RELAY_PROTOCOL:
                return relays.get(quicChannel);
            case IPFS.IDENTITY_PROTOCOL:
                return idents.get(quicChannel);
            case IPFS.BIT_SWAP_PROTOCOL:
                return bitSwaps.get(quicChannel);
            case IPFS.KAD_DHT_PROTOCOL:
                return kads.get(quicChannel);
            case IPFS.PUSH_PROTOCOL:
                return pushes.get(quicChannel);
            default:
                throw new RuntimeException("missing list for protocol");
        }
    }

    public void putStream(@NonNull QuicChannel quicChannel, @NonNull String protocol,
                          @NonNull QuicStreamChannel streamChannel) {
        switch (protocol) {
            case IPFS.RELAY_PROTOCOL:
                relays.put(quicChannel, streamChannel);
                break;
            case IPFS.IDENTITY_PROTOCOL:
                idents.put(quicChannel, streamChannel);
                break;
            case IPFS.BIT_SWAP_PROTOCOL:
                bitSwaps.put(quicChannel, streamChannel);
                break;
            case IPFS.KAD_DHT_PROTOCOL:
                kads.put(quicChannel, streamChannel);
                break;
            case IPFS.PUSH_PROTOCOL:
                pushes.put(quicChannel, streamChannel);
                break;
            default:
                throw new RuntimeException("missing list for protocol");
        }
    }

    public void removeStream(@NonNull QuicChannel quicChannel, @NonNull String protocol) {
        switch (protocol) {
            case IPFS.RELAY_PROTOCOL:
                relays.remove(quicChannel);
                break;
            case IPFS.IDENTITY_PROTOCOL:
                idents.remove(quicChannel);
                break;
            case IPFS.BIT_SWAP_PROTOCOL:
                bitSwaps.remove(quicChannel);
                break;
            case IPFS.KAD_DHT_PROTOCOL:
                kads.remove(quicChannel);
                break;
            case IPFS.PUSH_PROTOCOL:
                pushes.remove(quicChannel);
                break;
            default:
                throw new RuntimeException("missing list for protocol");
        }
    }

    public boolean swarmContains(@NonNull PeerId peerId) {
        return swarm.contains(peerId);
    }

    public static class LiteConnection implements Connection {
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
        public PeerId remoteId() {
            return channel.attr(LiteHost.PEER_KEY).get();
        }

        @Override
        public ChannelFuture close() {
            return channel.close();
        }


    }
}
