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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import bitswap.pb.MessageOuterClass;
import identify.pb.IdentifyOuterClass;
import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.bitswap.BitSwap;
import io.ipfs.bitswap.BitSwapMessage;
import io.ipfs.bitswap.BitSwapNetwork;
import io.ipfs.bitswap.BitSwapReceiver;
import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.core.ConnectionIssue;
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
import io.netty.util.AttributeKey;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.EmptyArrays;
import relay.pb.Relay;


public class LiteHost implements BitSwapReceiver, BitSwapNetwork {
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
    private final LiteHostCertificate selfSignedCertificate;
    @NonNull
    private final Set<PeerId> swarm = ConcurrentHashMap.newKeySet();
    @NonNull
    public List<ConnectionHandler> handlers = new ArrayList<>();
    @Nullable
    private Push push;
    @Nullable
    private Channel server;
    private InetAddress localAddress;
    private Channel client;

    public LiteHost(@NonNull LiteHostCertificate selfSignedCertificate, @NonNull PrivKey privKey,
                    @NonNull BlockStore blockstore, int port, int alpha) {
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
                .initialMaxData(IPFS.BLOCK_SIZE_LIMIT)
                .initialMaxStreamDataBidirectionalLocal(IPFS.BLOCK_SIZE_LIMIT)
                .initialMaxStreamDataBidirectionalRemote(IPFS.BLOCK_SIZE_LIMIT)
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


    @Override
    public PeerId Self() {
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

    @Override
    public void findProviders(@NonNull Closeable closeable, @NonNull Routing.Providers providers,
                              @NonNull Cid cid) throws ClosedException {
        routing.findProviders(closeable, providers, cid);
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
    private List<Multiaddr> prepareAddresses(@NonNull PeerId peerId, boolean ipv6) {
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
            if (AddrInfo.isSupported(addr)) {
                if (ipv6 && addr.has(Protocol.Type.IP6)) {
                    result.add(addr);
                } else if (!ipv6 && addr.has(Protocol.Type.IP4)) {
                    result.add(addr);
                } else {
                    LogUtils.verbose(TAG, "Ignore " + addr.toString());
                }
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
    public PeerInfo getPeerInfo(@NonNull Closeable closeable,
                                @NonNull Connection conn) throws ClosedException {

        try {
            IdentifyOuterClass.Identify identify = IdentityService.getIdentity(closeable, conn);
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
        return new HashSet<>(swarm);
    }

    public boolean canHop(@NonNull Closeable closeable, @NonNull PeerId peerId)
            throws ConnectionIssue, ClosedException {

        try {
            Connection conn = connect(closeable, peerId, IPFS.CONNECT_TIMEOUT);

            Relay.CircuitRelay msg = RelayService.getRelay(closeable, conn);

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


        Connection connection = connections.get(peerId);
        if (connection != null) {
            return connection;
        }

        List<Multiaddr> addrInfo = prepareAddresses(peerId, inet6());

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

            /* sometimes it worked
             long addr = conn.channel().connection();
            if(QuicheWrapper.isClosed(addr)){
                LogUtils.error(TAG, "Connection closed !!!");
            } else {
                LogUtils.error(TAG, "Connection not closed !!!");
            }*/

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


    public void protectPeer(@NonNull PeerId peerId) {
        tags.add(peerId);
    }

    public void unprotectPeer(@NonNull PeerId peerId) {
        tags.remove(peerId);
    }

    public boolean isProtected(@NonNull PeerId peerId) {
        return tags.contains(peerId);
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
