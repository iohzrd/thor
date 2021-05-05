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
import java.time.Duration;
import java.util.ArrayList;
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
import io.ipfs.IPFS;
import io.ipfs.bitswap.BitSwap;
import io.ipfs.bitswap.BitSwapMessage;
import io.ipfs.bitswap.BitSwapNetwork;
import io.ipfs.bitswap.BitSwapReceiver;
import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.core.ConnectionIssue;
import io.ipfs.core.ProtocolIssue;
import io.ipfs.core.TimeoutIssue;
import io.ipfs.dht.KadDHT;
import io.ipfs.dht.Routing;
import io.ipfs.exchange.Interface;
import io.ipfs.format.BlockStore;
import io.ipfs.multibase.Charsets;
import io.ipfs.multihash.Multihash;
import io.ipfs.relay.Relay;
import io.ipns.Ipns;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multiformats.Protocol;
import io.libp2p.etc.types.NonCompleteException;
import io.libp2p.etc.types.NothingToCompleteException;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.util.concurrent.Promise;


public class LiteHost implements BitSwapReceiver, BitSwapNetwork, Metrics {
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
    private final Relay relay;
    @NonNull
    private final Interface exchange;
    @Nullable
    private Channel client;

    public LiteHost(@NonNull PrivKey privKey, @NonNull BlockStore blockstore, int port, int alpha) {

        this.privKey = privKey;


        this.routing = new KadDHT(this,
                new Ipns(), alpha, IPFS.KAD_DHT_BETA,
                IPFS.KAD_DHT_BUCKET_SIZE);

        this.exchange = BitSwap.create(this, blockstore);
        this.relay = new Relay(this);


        ChannelHandler codec = new QuicClientCodecBuilder()
                .sslContext(IPFS.CLIENT_SSL_INSTANCE)
                .maxIdleTimeout(30, TimeUnit.SECONDS)
                .initialMaxData(IPFS.BLOCK_SIZE_LIMIT)
                .initialMaxStreamDataBidirectionalLocal(IPFS.BLOCK_SIZE_LIMIT)
                .initialMaxStreamDataBidirectionalRemote(IPFS.BLOCK_SIZE_LIMIT)
                .initialMaxStreamsBidirectional(200)
                .initialMaxStreamsUnidirectional(100)
                //.datagram(10000000, 10000000)
                .build();

        try {
            Bootstrap bs = new Bootstrap();
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

    public List<ConnectionHandler> handlers = new ArrayList<>();


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
        for (Connection conn: connections.values()) {
            if(conn.channel().isOpen()) {
                conns.add(conn);
            }
        }

        return conns;
    }

    private void forwardMessage(@NonNull PeerId peerId, @NonNull MessageLite msg){
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
                             @NonNull BitSwapMessage message)
            throws ClosedException, ProtocolIssue, TimeoutIssue, ConnectionIssue {


        try {

            Connection con = connect(closeable, peerId);
            active(peerId);
            MessageLite msg = request(closeable, IPFS.BITSWAP_PROTOCOL, con, message.ToProtoV1());
            forwardMessage(peerId, msg);// TODO why has it no success

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
                if (cause instanceof NothingToCompleteException) {
                    throw new ConnectionIssue();
                }
                if (cause instanceof NonCompleteException) {
                    throw new ConnectionIssue();
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
            throws ClosedException, ConnectionIssue {
        return relay.canHop(closeable, peerId);
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
                        if(future.isSuccess()){
                            break;
                        }
                    }
                    if (closeable.isClosed()) {
                        throw new ClosedException();
                    }


                    QuicChannel quic = future.get();
                    Objects.requireNonNull(quic);

                    connection = new LiteConnection(peerId, quic);
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
                     @NonNull Connection conn, @NonNull MessageLite message)
            throws InterruptedException, ExecutionException, ClosedException {

        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        QuicChannel quicChannel = conn.channel();


        CompletableFuture<Void> ctrl = send(quicChannel, protocol, message);


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
                LogUtils.error(TAG, "Abort " + protocol);
                ctrl.cancel(true);
            }
        }

        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        MessageLite res = ctrl.get();
        LogUtils.error(TAG, "success request  " + res.getClass().getSimpleName());
        return res;
    }


    private ByteBuf encode(@NonNull MessageLite message) {
        byte[] data = message.toByteArray();
        try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            Multihash.putUvarint(buf, data.length);
            buf.write(data);
            return Unpooled.buffer().writeBytes(buf.toByteArray());
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }


    public CompletableFuture<Void> send(@NonNull QuicChannel quicChannel,
                                        @NonNull String protocol,
                                        @NonNull MessageLite message) {


        LogUtils.error(TAG, protocol);
        CompletableFuture<Void> ret = new CompletableFuture<>();

        try {
            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new SimpleChannelInboundHandler<Object>() {
                        private final DataReader reader = new DataReader(IPFS.BLOCK_SIZE_LIMIT);
                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                throws Exception {
                            LogUtils.error(TAG, cause.getClass().getSimpleName());
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
                                    LogUtils.error(TAG, "send " + received);
                                    if (Objects.equals(received, IPFS.NA)) {
                                        throw new ProtocolIssue();
                                    } else if (Objects.equals(received, IPFS.STREAM_PROTOCOL)) {
                                    } else if (Objects.equals(received, protocol)) {
                                        ret.complete(ctx.writeAndFlush(
                                                encode(message)).addListener(
                                                (ChannelFutureListener) future -> ctx.close()).get());
                                    } else {
                                        throw new ProtocolIssue();
                                    }
                                }
                            } else {
                                LogUtils.error(TAG, "iteration " + data.length + " "
                                        + reader.expectedBytes() + " " + protocol + " " + ctx.name() + " "
                                        + ctx.channel().remoteAddress());
                            }
                        }
                    }).sync().get(IPFS.TIMEOUT_SEND, TimeUnit.SECONDS);


            streamChannel.pipeline().addFirst(new ReadTimeoutHandler(10, TimeUnit.SECONDS));


            LogUtils.error(TAG, streamChannel.pipeline().names().toString());
            streamChannel.writeAndFlush(writeToken(IPFS.STREAM_PROTOCOL));
            streamChannel.writeAndFlush(writeToken(protocol));


        } catch (Throwable throwable) {
            // TODO rethink
            ret.completeExceptionally(throwable);
        }

        return ret;
    }

    // TODO evaluate if it makes sens
    public int evalMaxResponseLength(@NonNull String protocol){
        if( protocol.equals(IPFS.IDENTITY_PROTOCOL) ||
           protocol.equals(IPFS.KAD_DHT_PROTOCOL) ){
            return 25000;
        }
        return IPFS.BLOCK_SIZE_LIMIT;
    }

    public CompletableFuture<MessageLite> request(@NonNull QuicChannel quicChannel,
                                                  @NonNull String protocol,
                                                  @Nullable MessageLite message){

        LogUtils.error(TAG, protocol);
        CompletableFuture<MessageLite> ret = new CompletableFuture<>();
        CompletableFuture<Void> activation = new CompletableFuture<>();

        try {
            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new SimpleChannelInboundHandler<Object>() {

                        private DataReader reader = new DataReader(IPFS.BLOCK_SIZE_LIMIT);
                        private boolean negotiation = true;


                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                throws Exception {
                            LogUtils.error(TAG, cause.getClass().getSimpleName());
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
                                        LogUtils.error(TAG, "request " + received);
                                        if (Objects.equals(received, IPFS.NA)) {
                                            throw new ProtocolIssue();
                                        } else if (Objects.equals(received, IPFS.STREAM_PROTOCOL)) {
                                        } else if (Objects.equals(received, protocol)) {
                                            negotiation = false;
                                            if (message != null) {
                                                activation.complete(
                                                        ctx.writeAndFlush(encode(message))
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
                                            case IPFS.IDENTITY_PROTOCOL:
                                                LogUtils.error(TAG + "FFFF", "Found " + protocol);
                                                ret.complete(IdentifyOuterClass.Identify.parseFrom(message));
                                                break;
                                            case IPFS.BITSWAP_PROTOCOL:
                                                LogUtils.error(TAG + "FFFF", "Found " + protocol);
                                                ret.complete(MessageOuterClass.Message.parseFrom(message));
                                                break;
                                            case IPFS.KAD_DHT_PROTOCOL:
                                                LogUtils.error(TAG + "FFFF", "Found " + protocol);
                                                ret.complete(Dht.Message.parseFrom(message));
                                                break;
                                            default:
                                                throw new Exception("unknown protocol");
                                        }
                                        ctx.close();
                                    }
                                    reader = new DataReader(evalMaxResponseLength(protocol));
                                } else {
                                    LogUtils.error(TAG, "iteration " + data.length + " "
                                            + reader.expectedBytes() + " " + protocol + " " + ctx.name() + " "
                                            + ctx.channel().remoteAddress());
                                }

                            } else {
                                if (reader.isDone()) {
                                    byte[] message = reader.getMessage();
                                    switch (protocol) {
                                        case IPFS.IDENTITY_PROTOCOL:
                                            LogUtils.error(TAG + "FFFF", "Found " + protocol);
                                            ret.complete(IdentifyOuterClass.Identify.parseFrom(message));
                                            break;
                                        case IPFS.BITSWAP_PROTOCOL:
                                            LogUtils.error(TAG + "FFFF", "Found " + protocol);
                                            ret.complete(MessageOuterClass.Message.parseFrom(message));
                                            break;
                                        case IPFS.KAD_DHT_PROTOCOL:
                                            LogUtils.error(TAG + "FFFF", "Found " + protocol);
                                            ret.complete(Dht.Message.parseFrom(message));
                                            break;
                                        default:
                                            throw new Exception("unknown protocol");
                                    }
                                    ctx.close();

                                } else {
                                    LogUtils.error(TAG, "iteration " + data.length + " "
                                            + reader.expectedBytes() + " " + protocol + " " + ctx.name() + " "
                                            + ctx.channel().remoteAddress());
                                }
                            }
                        }
                    }).sync().get(IPFS.TIMEOUT_REQUEST, TimeUnit.SECONDS);


            streamChannel.pipeline().addFirst(new ReadTimeoutHandler(10, TimeUnit.SECONDS));

            LogUtils.error(TAG, streamChannel.pipeline().names().toString());

            streamChannel.writeAndFlush(writeToken(IPFS.STREAM_PROTOCOL));
            streamChannel.writeAndFlush(writeToken(protocol));

        } catch (Throwable throwable) {
            activation.completeExceptionally(throwable);
            ret.completeExceptionally(throwable);
        }

        return activation.thenCompose(s -> ret);
    }


    public Promise<QuicChannel> dial(@NonNull Multiaddr multiaddr,
                                     @Nullable PeerId peerId) throws Exception {

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

                .streamHandler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        // As we did not allow any remote initiated streams we will never see this method called.
                        // That said just let us keep it here to demonstrate that this handle would be called
                        // for each remote initiated stream.


                        LogUtils.error(TAG, "Channel active " + ctx.name());


                        QuicStreamChannel quicChannel = (QuicStreamChannel) ctx.channel();
                        LogUtils.error(TAG + "CLIENT", "Sreaming ID " + quicChannel.remoteAddress().streamId());


                            quicChannel.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {

                                private DataReader reader = new DataReader(IPFS.BLOCK_SIZE_LIMIT);
                                private String lastProtocol;


                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                    LogUtils.error(TAG, cause.getClass().getSimpleName());
                                    ctx.close().get();
                                }


                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, Object value) throws Exception {


                                    ByteBuf msg = (ByteBuf) value;
                                    Objects.requireNonNull(msg);

                                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                                    msg.readBytes(out, msg.readableBytes());
                                    byte[] data = out.toByteArray();
                                    reader.load(data);

                                    if (reader.isDone()) {
                                        for (String token : reader.getTokens()) {
                                            LogUtils.error(TAG + "CLIENT", "TOKEN " + token);
                                            if (token.endsWith(IPFS.STREAM_PROTOCOL)) {
                                                ctx.writeAndFlush(writeToken(IPFS.STREAM_PROTOCOL));
                                            } else if (token.endsWith(IPFS.BITSWAP_PROTOCOL)) {
                                                lastProtocol = IPFS.BITSWAP_PROTOCOL;
                                                ctx.writeAndFlush(writeToken(IPFS.BITSWAP_PROTOCOL)).get();
                                            } else if (token.endsWith(IPFS.IDENTITY_PROTOCOL)) {
                                                //lastProtocol = IPFS.IDENTITY_PROTOCOL;
                                                ctx.write(writeToken(IPFS.IDENTITY_PROTOCOL));

                                                try {
                                                    // TODO this is not correct
                                                    Multiaddr multiaddr = new Multiaddr("/ip4/127.0.0.1/udp/5001/quic");

                                                    IdentifyOuterClass.Identify response =
                                                            IdentifyOuterClass.Identify.newBuilder()
                                                                    .setAgentVersion(IPFS.AGENT)
                                                                    .setPublicKey(ByteString.copyFrom(privKey.publicKey().bytes()))
                                                                    .setProtocolVersion(IPFS.PROTOCOL_VERSION)

                                                                    .setObservedAddr(ByteString.copyFrom(multiaddr.getBytes()))
                                                                    .build();
                                                    ctx.writeAndFlush(encode(response)).get();
                                                    ctx.close();
                                                } catch (Throwable throwable) {
                                                    LogUtils.error(TAG, throwable);
                                                }
                                            }
                                        }
                                        reader = new DataReader(IPFS.BLOCK_SIZE_LIMIT); // TODO
                                    } else {
                                        LogUtils.error(TAG, "iteration " + data.length + " "
                                                + reader.expectedBytes() + " " + ctx.name() + " "
                                                + ctx.channel().remoteAddress());
                                    }
                                }
                            });

                    }
                })
                .remoteAddress(new InetSocketAddress(inetAddress, port))
                .connect();


    }

    private ByteBuf writeToken(String token) {

        token = token.concat("\n");

        byte[] data = token.getBytes(Charsets.UTF_8);
        try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            Multihash.putUvarint(buf, data.length);
            buf.write(data);
            return Unpooled.buffer().writeBytes(buf.toByteArray());
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
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
        private final PeerId peerId;

        public LiteConnection(@NonNull PeerId peerId, @NonNull QuicChannel channel) {
            this.peerId = peerId;
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
            return peerId;
        }

        @Override
        public ChannelFuture close() {
            return channel.close();
        }


    }
}
