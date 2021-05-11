package io.ipfs.host;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
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
import io.ipfs.multiformats.Multiaddr;
import io.ipfs.multiformats.Protocol;
import io.ipns.Ipns;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Promise;
import io.quic.QuicheWrapper;


public class LiteHost implements BitSwapReceiver, BitSwapNetwork, Metrics {
    public static final AttributeKey<PeerId> PEER_KEY = AttributeKey.newInstance("PEER_KEY");
    private static final String TAG = LiteHost.class.getSimpleName();
    private static final Duration DefaultRecordEOL = Duration.ofHours(24);
    private final ConcurrentHashMap<PeerId, Long> metrics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PeerId, Long> actives = new ConcurrentHashMap<>();
    private final Set<PeerId> tags = ConcurrentHashMap.newKeySet();
    private final NioEventLoopGroup group = new NioEventLoopGroup(1);
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
    public List<ConnectionHandler> handlers = new ArrayList<>();
    private Channel client;
    private Pusher pusher;

    public LiteHost(@NonNull PrivKey privKey, @NonNull BlockStore blockstore, int port, int alpha) {

        this.privKey = privKey;
        this.port = port;


        this.routing = new KadDHT(this,
                new Ipns(), alpha, IPFS.KAD_DHT_BETA,
                IPFS.KAD_DHT_BUCKET_SIZE);

        this.exchange = BitSwap.create(this, blockstore);


        ChannelHandler codec = new QuicClientCodecBuilder()
                .sslContext(IPFS.CLIENT_SSL_INSTANCE)
                .maxIdleTimeout(30, TimeUnit.SECONDS)
                .initialMaxData(IPFS.BLOCK_SIZE_LIMIT)
                .initialMaxStreamDataBidirectionalLocal(IPFS.BLOCK_SIZE_LIMIT)
                .initialMaxStreamDataBidirectionalRemote(IPFS.BLOCK_SIZE_LIMIT)
                .initialMaxStreamsBidirectional(IPFS.HIGH_WATER * 5) // TODO rethink
                .initialMaxStreamsUnidirectional(IPFS.HIGH_WATER * 5) // TODO rethink
                //.tokenHandler(InsecureQuicTokenHandler.INSTANCE)
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

        /*
        ChannelHandler codec = new QuicServerCodecBuilder().sslContext(IPFS.SERVER_SSL_INSTANCE)
                // Configure some limits for the maximal number of streams (and the data) that we want to handle.
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)


                // Setup a token handler. In a production system you would want to implement and provide your custom
                // one.
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                // ChannelHandler that is added into QuicChannel pipeline.
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        QuicChannel channel = (QuicChannel) ctx.channel();
                        // Create streams etc..
                    }

                    public void channelInactive(ChannelHandlerContext ctx) {
                        ((QuicChannel) ctx.channel()).collectStats().addListener(f -> {
                            if (f.isSuccess()) {
                                LogUtils.error(TAG, "success");
                            }
                        });
                    }

                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                })
                .streamHandler(new ChannelInitializer<QuicStreamChannel>() {


                    @Override
                    protected void initChannel(QuicStreamChannel ch) {

                        // todo peerid
                        ch.pipeline().addLast(new DataStreamHandler(LiteHost.this,
                                null, pusher));
                    }
                }).build();
        try {
            Bootstrap bs = new Bootstrap();
            server = bs.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(codec)
                    .bind(port).sync().channel();
            //channel.closeFuture().sync();


        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }*/

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
    public void ReceiveMessage(@NonNull PeerId peer, @NonNull String protocol, @NonNull BitSwapMessage incoming) {
        exchange.ReceiveMessage(peer, protocol, incoming);
    }

    @Override
    public void ReceiveError(@NonNull PeerId peer, @NonNull String protocol, @NonNull String error) {
        exchange.ReceiveError(peer, protocol, error);
    }

    @Override
    public boolean GatePeer(PeerId peerID) {
        return exchange.GatePeer(peerID);
    }

    @Override
    public boolean connectTo(@NonNull Closeable closeable, @NonNull PeerId peerId)
            throws ClosedException, ConnectionIssue {

        Connection con = connect(closeable, peerId);

        return true; // TODO maybe not return anything, because an exception is thrown
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

    public void forwardMessage(@NonNull PeerId peerId, @NonNull MessageLite msg) {
        if (msg instanceof MessageOuterClass.Message) {
            new Thread(() -> {
                try {
                    BitSwapMessage message = BitSwapMessage.newMessageFromProto(
                            (MessageOuterClass.Message) msg);
                    ReceiveMessage(peerId, IPFS.BITSWAP_PROTOCOL, message);
                } catch (Throwable throwable) {
                    ReceiveError(peerId, IPFS.BITSWAP_PROTOCOL, "" + throwable.getMessage());
                }
            }).start();
        }
    }

    @Override
    public void writeMessage(@NonNull Closeable closeable, @NonNull PeerId peerId,
                             @NonNull BitSwapMessage message, boolean urgentPriority)
            throws ClosedException, ProtocolIssue, TimeoutIssue, ConnectionIssue {


        try {

            Connection con = connect(closeable, peerId);
            active(peerId);
            send(closeable, IPFS.BITSWAP_PROTOCOL, con,
                    message.ToProtoV1().toByteArray(), urgentPriority);

        } catch (ClosedException | ConnectionIssue exception) {
            done(peerId);
            throw exception;
        } catch (Throwable throwable) {
            done(peerId);
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

    @Override
    public void findProviders(@NonNull Closeable closeable, @NonNull Routing.Providers providers,
                              @NonNull Cid cid) throws ClosedException {
        routing.FindProviders(closeable, providers, cid);
    }

    @NonNull
    public PeerInfo getPeerInfo(@NonNull Closeable closeable,
                                @NonNull Connection conn) throws ClosedException {

        try {
            MessageLite message = request(closeable, IPFS.IDENTITY_PROTOCOL, conn, null);
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
                if (ma.has(Protocol.DNS6)) {
                    all.add(DnsResolver.resolveDns6(ma));
                } else if (ma.has(Protocol.DNS4)) {
                    all.add(DnsResolver.resolveDns4(ma));
                } else if (ma.has(Protocol.DNSADDR)) {
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

    @NonNull
    @Override
    public Set<PeerId> getPeers() {
        Set<PeerId> peerIds = new HashSet<>();
        for (Connection connection : getConnections()) {
            peerIds.add(connection.remoteId());
        }

        return peerIds;
    }

    public boolean canHop(@NonNull Closeable closeable, @NonNull PeerId peerId)
            throws ConnectionIssue, ClosedException {


        relay.pb.Relay.CircuitRelay message = relay.pb.Relay.CircuitRelay.newBuilder()
                .setType(relay.pb.Relay.CircuitRelay.Type.CAN_HOP)
                .build();

        try {
            Connection conn = connect(closeable, peerId);
            MessageLite messageLite = request(closeable, IPFS.RELAY_PROTOCOL, conn, message);
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

    // TODO should be improved with information of the device real
    // public IP, probably asks other devices for getting
    // the real IP address (relay, punch-hole, etc stuff
    @NonNull
    public List<Multiaddr> listenAddresses() {
        try {
            // TODO the listen address does not contain real IP address

            List<Multiaddr> list = new ArrayList<>();
            list.add(new Multiaddr("/ip4/127.0.0.1/udp/" + port + "/quic")); // TODO default values
            list.add(new Multiaddr("/ip4/127.0.0.1/udp/" + port + "/quic")); // TODO default values

            return list;
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return Collections.emptyList();

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

    @NonNull
    public Connection connect(@NonNull Closeable closeable, @NonNull PeerId peerId)
            throws ConnectionIssue, ClosedException {


        if (closeable.isClosed()) {
            throw new ClosedException();
        }
        Connection connection = connections.get(peerId);
        if (connection != null) {
            return connection;
        }
        List<Multiaddr> addrInfo = prepareAddresses(peerId);

        if (!addrInfo.isEmpty()) {
            int size = addrInfo.size();
            for (Multiaddr address : addrInfo) {
                try {
                    size--;

                    Promise<QuicChannel> future = dial(address, peerId);

                    while (!future.isCancelled()) {
                        if (closeable.isClosed()) {
                            future.cancel(true);
                            break;
                        }
                        if (future.isSuccess()) {
                            break;
                        }
                    }
                    if (closeable.isClosed()) {
                        throw new ClosedException();
                    }


                    QuicChannel quic = future.get();
                    Objects.requireNonNull(quic);


                    connection = new LiteConnection(quic);
                    quic.closeFuture().addListener(future1 -> connections.remove(peerId));
                    addConnection(connection);

                    return connection;
                } catch (ClosedException closedException) {
                    throw closedException;
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                    if (size <= 0) {
                        throw new ConnectionIssue();
                    }
                }
            }
        }
        throw new RuntimeException("No address available");

    }

    public void send(@NonNull Closeable closeable, @NonNull String protocol,
                     @NonNull Connection conn, @NonNull byte[] data,
                     boolean urgentPriority)
            throws InterruptedException, ExecutionException, ClosedException {

        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        QuicChannel quicChannel = conn.channel();


        CompletableFuture<Void> ctrl = send(quicChannel, protocol, data, urgentPriority);


        while (!ctrl.isDone()) {
            if (closeable.isClosed()) {
                ctrl.cancel(true);
            }
        }

        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        ctrl.get();
    }

    @NonNull
    public MessageLite request(@NonNull Closeable closeable, @NonNull String protocol,
                               @NonNull Connection conn, @Nullable MessageLite message)
            throws InterruptedException, ExecutionException, ClosedException {


        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        QuicChannel quicChannel = conn.channel();

        CompletableFuture<MessageLite> ctrl = request(quicChannel, protocol, message);


        while (!ctrl.isDone()) {
            if (closeable.isClosed()) {
                ctrl.cancel(true);
            }
        }

        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        MessageLite res = ctrl.get();
        LogUtils.verbose(TAG, "success request  " + res.getClass().getSimpleName());
        return res;
    }

    public CompletableFuture<Void> send(@NonNull QuicChannel quicChannel,
                                        @NonNull String protocol,
                                        @NonNull byte[] message,
                                        boolean urgentPriority) {


        CompletableFuture<Void> ret = new CompletableFuture<>();

        try {
            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new SimpleChannelInboundHandler<Object>() {
                        private DataHandler reader = new DataHandler(IPFS.BLOCK_SIZE_LIMIT);

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                throws Exception {
                            LogUtils.error(TAG, cause);
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
                    }).sync().get(IPFS.TIMEOUT_SEND, TimeUnit.SECONDS);

            if (urgentPriority) {
                streamChannel.updatePriority(QuicheWrapper.URGENT);
            }

            streamChannel.write(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
            streamChannel.writeAndFlush(DataHandler.writeToken(protocol));


        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            ret.completeExceptionally(throwable);
        }

        return ret;
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

    public CompletableFuture<MessageLite> request(@NonNull QuicChannel quicChannel,
                                                  @NonNull String protocol,
                                                  @Nullable MessageLite message) {

        CompletableFuture<MessageLite> ret = new CompletableFuture<>();
        CompletableFuture<Void> activation = new CompletableFuture<>();

        try {
            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new SimpleChannelInboundHandler<Object>() {

                        private DataHandler reader = new DataHandler(IPFS.BLOCK_SIZE_LIMIT);
                        private boolean negotiation = true;


                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                throws Exception {
                            LogUtils.error(TAG, cause);
                            ret.completeExceptionally(cause);
                            activation.completeExceptionally(cause);
                            ctx.close().get();
                        }

                        @Override
                        public void channelUnregistered(ChannelHandlerContext ctx) {
                            ret.completeExceptionally(new ConnectionIssue());
                            activation.completeExceptionally(new ConnectionIssue());
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

                            if (negotiation) {
                                if (reader.isDone()) {

                                    for (String received : reader.getTokens()) {
                                        LogUtils.debug(TAG, "request " + received);
                                        if (Objects.equals(received, IPFS.NA)) {
                                            throw new ProtocolIssue();
                                        } else if (Objects.equals(received, IPFS.STREAM_PROTOCOL)) {
                                        } else if (Objects.equals(received, protocol)) {
                                            negotiation = false;
                                            if (message != null) {
                                                activation.complete(
                                                        ctx.writeAndFlush(DataHandler.encode(message))
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
                                                ret.complete(relay.pb.Relay.CircuitRelay.parseFrom(message));
                                                break;
                                            case IPFS.IDENTITY_PROTOCOL:
                                                LogUtils.debug(TAG, "Found " + protocol);
                                                ret.complete(IdentifyOuterClass.Identify.parseFrom(message));
                                                break;
                                            case IPFS.BITSWAP_PROTOCOL:
                                                LogUtils.debug(TAG, "Found " + protocol);
                                                ret.complete(MessageOuterClass.Message.parseFrom(message));
                                                break;
                                            case IPFS.KAD_DHT_PROTOCOL:
                                                LogUtils.debug(TAG, "Found " + protocol);
                                                ret.complete(Dht.Message.parseFrom(message));
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

                            } else {
                                if (reader.isDone()) {

                                    byte[] message = reader.getMessage();

                                    switch (protocol) {
                                        case IPFS.RELAY_PROTOCOL:
                                            LogUtils.debug(TAG, "Found " + protocol);
                                            ret.complete(relay.pb.Relay.CircuitRelay.parseFrom(message));
                                            break;
                                        case IPFS.IDENTITY_PROTOCOL:
                                            LogUtils.debug(TAG, "Found " + protocol);
                                            ret.complete(IdentifyOuterClass.Identify.parseFrom(message));
                                            break;
                                        case IPFS.BITSWAP_PROTOCOL:
                                            LogUtils.debug(TAG, "Found " + protocol);
                                            ret.complete(MessageOuterClass.Message.parseFrom(message));
                                            break;
                                        case IPFS.KAD_DHT_PROTOCOL:
                                            LogUtils.debug(TAG, "Found " + protocol);
                                            ret.complete(Dht.Message.parseFrom(message));
                                            break;
                                        default:
                                            throw new Exception("unknown protocol");
                                    }
                                    ctx.close();

                                } else {
                                    LogUtils.debug(TAG, "iteration " + reader.hasRead() + " "
                                            + reader.expectedBytes() + " " + protocol + " " + ctx.name() + " "
                                            + ctx.channel().remoteAddress());
                                }
                            }
                        }
                    }).sync().get(IPFS.TIMEOUT_REQUEST, TimeUnit.SECONDS);


            streamChannel.pipeline().addFirst(new ReadTimeoutHandler(10, TimeUnit.SECONDS));


            streamChannel.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
            streamChannel.writeAndFlush(DataHandler.writeToken(protocol));

        } catch (Throwable throwable) {
            activation.completeExceptionally(throwable);
            ret.completeExceptionally(throwable);
        }

        return activation.thenCompose(s -> ret);
    }

    public void setPusher(@Nullable Pusher pusher) {
        this.pusher = pusher;
    }


    public Promise<QuicChannel> dial(@NonNull Multiaddr multiaddr, @NonNull PeerId peerId) throws Exception {

        InetAddress inetAddress;
        if (multiaddr.has(Protocol.IP4)) {
            inetAddress = Inet4Address.getByName(multiaddr.getStringComponent(Protocol.IP4));
        } else if (multiaddr.has(Protocol.IP6)) {
            inetAddress = Inet6Address.getByName(multiaddr.getStringComponent(Protocol.IP6));
        } else {
            throw new RuntimeException();
        }
        int port = multiaddr.udpPortFromMultiaddr();


        return (Promise<QuicChannel>) QuicChannel.newBootstrap(client)
                .attr(PEER_KEY, peerId)
                .streamHandler(new WelcomeHandler(LiteHost.this))
                .remoteAddress(new InetSocketAddress(inetAddress, port))
                .connect();


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
            if (client != null) {
                client.closeFuture().sync();
            }
            group.shutdownGracefully();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        } finally {
            client = null;
        }
    }

    private boolean isActive(@NonNull PeerId peerId) {
        Long time = actives.get(peerId);
        if (time != null) {
            int gracePeriod = IPFS.GRACE_PERIOD;
            return (System.currentTimeMillis() - time) < (gracePeriod * 1000);
        }
        return false;
    }

    public long getLatency(@NonNull PeerId peerId) {
        Long duration = metrics.get(peerId);
        if (duration != null) {
            return duration;
        }
        return Long.MAX_VALUE;
    }

    public void addLatency(@NonNull PeerId peerId, long latency) {
        metrics.put(peerId, latency);
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

    @Override
    public void active(@NonNull PeerId peerId) {
        actives.put(peerId, System.currentTimeMillis());
    }

    @Override
    public void done(@NonNull PeerId peerId) {
        actives.remove(peerId);
    }

    public void trimConnections() {

        int numConns = numConnections();
        LogUtils.verbose(TAG, "numConnections (before) " + numConns);

        int highWater = IPFS.HIGH_WATER;
        if (numConns > highWater) {

            int lowWater = IPFS.LOW_WATER;
            int hasToBeClosed = numConns - lowWater;

            // TODO maybe sort connections how fast they are (the fastest will not be closed)

            for (Connection connection : getConnections()) {
                if (hasToBeClosed > 0) {
                    try {
                        PeerId peerId = connection.remoteId();
                        if (!isProtected(peerId) && !isActive(peerId)) {
                            connection.close().get();
                            connections.remove(peerId);
                            hasToBeClosed--;
                            done(peerId);
                        }
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                }
            }
        }
        LogUtils.verbose(TAG, "numConnections (after) " + numConnections());
    }


    public int numConnections() {
        return getConnections().size();
    }

    public static class LiteConnection implements Connection {
        private final QuicChannel channel;


        public LiteConnection(@NonNull QuicChannel channel) {
            this.channel = channel;
        }

        // BIG TODO
        @NotNull
        @Override
        public Multiaddr remoteAddress() {
            //throw new RuntimeException("TODO");
            return null;
        }

        @Override
        public QuicChannel channel() {
            return channel;
        }


        @NotNull
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
