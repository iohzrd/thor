package io.ipfs.host;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.collect.Iterables;
import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

import org.jetbrains.annotations.NotNull;

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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
import io.ipfs.core.AddrInfo;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.core.ConnectionIssue;
import io.ipfs.core.PeerInfo;
import io.ipfs.core.ProtocolIssue;
import io.ipfs.core.TimeoutIssue;
import io.ipfs.dht.KadDHT;
import io.ipfs.dht.Routing;
import io.ipfs.exchange.Interface;
import io.ipfs.format.BlockStore;
import io.ipfs.multibase.Charsets;
import io.ipfs.relay.Relay;
import io.ipns.Ipns;
import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionClosedException;
import io.libp2p.core.ConnectionHandler;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multiformats.Protocol;
import io.libp2p.core.mux.StreamMuxer;
import io.libp2p.core.security.SecureChannel;
import io.libp2p.core.transport.Transport;
import io.libp2p.etc.types.NonCompleteException;
import io.libp2p.etc.types.NothingToCompleteException;
import io.libp2p.etc.util.netty.StringSuffixCodec;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import kotlin.Unit;


public class LiteHost implements BitSwapReceiver, BitSwapNetwork {
    private static final String TAG = LiteHost.class.getSimpleName();
    private static final Duration DefaultRecordEOL = Duration.ofHours(24);

    @NonNull
    private final ConcurrentHashMap<PeerId, Set<Multiaddr>> addressBook = new ConcurrentHashMap<>();
    @NonNull
    private final ConcurrentHashMap<PeerId, Connection> connections = new ConcurrentHashMap<>();
    @NonNull
    private final Routing routing;
    @NonNull
    private final ConnectionManager metrics;
    @NonNull
    private final PrivKey privKey;
    @NonNull
    private final Relay relay;

    @NonNull
    private final Interface exchange;

    public LiteHost(@NonNull PrivKey privKey, @NonNull BlockStore blockstore, int alpha) {

        this.privKey = privKey;
        this.metrics = new ConnectionManager(this, IPFS.LOW_WATER, IPFS.HIGH_WATER, IPFS.GRACE_PERIOD);

        this.routing = new KadDHT(this,
                new Ipns(), alpha, IPFS.KAD_DHT_BETA,
                IPFS.KAD_DHT_BUCKET_SIZE);

        this.exchange = BitSwap.create(this, blockstore);
        this.relay = new Relay(this);
    }

    private final NioEventLoopGroup group = new NioEventLoopGroup(1);

    @NonNull
    public Routing getRouting() {
        return routing;
    }

    public List<ConnectionHandler> handlers = new ArrayList<>();
    private Channel server;

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
        return new ArrayList<>(connections.values()); // TODO maybe optimize
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
    public void writeMessage(@NonNull Closeable closeable, @NonNull PeerId peer,
                             @NonNull BitSwapMessage message)
            throws ClosedException, ProtocolIssue, TimeoutIssue, ConnectionIssue {


        try {
            synchronized (peer.toBase58().intern()) {
                Connection con = connect(closeable, peer);
                metrics.active(peer);
                MessageLite msg = request(closeable, IPFS.BITSWAP_PROTOCOL, con, message.ToProtoV1());
                Objects.requireNonNull(msg);
                forwardMessage(peer, msg);
            }
        } catch (ClosedException | ConnectionIssue exception) {
            metrics.done(peer);
            throw exception;
        } catch (Throwable throwable) {
            metrics.done(peer);
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
                if (cause instanceof ConnectionClosedException) {
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

        /*  todo seperate them by protocol QUIC when supported and rest
        all.sort((o1, o2) -> {

            // TODO better sorting
            int result = Boolean.compare(o1.has(Protocol.QUIC), o2.has(Protocol.QUIC));
            if (result == 0) {
                result = Boolean.compare(o1.has(Protocol.TCP), o2.has(Protocol.TCP));
            }
            return result;
        });*/
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

    public Metrics getMetrics() {
        return metrics;
    }

    @NonNull
    public Connection connect(@NonNull Closeable closeable, @NonNull PeerId peerId)
            throws ConnectionIssue, ClosedException {

        try {
            if (closeable.isClosed()) {
                throw new ClosedException();
            }
            Connection connection = connections.get(peerId);
            if (connection != null) {
                return connection;
            }
            List<Multiaddr> addrInfo = prepareAddresses(peerId);

            if (!addrInfo.isEmpty()) {

                CompletableFuture<Connection> future = dial(peerId,
                        Iterables.toArray(addrInfo, Multiaddr.class));
                while (!future.isDone()) {
                    if (closeable.isClosed()) {
                        future.cancel(true);
                    }
                }
                if (closeable.isClosed()) {
                    throw new ClosedException();
                }

                LogUtils.error(TAG, "Success " + addrInfo.toString());

                connection = future.get();
                Objects.requireNonNull(connection);
                connections.put(peerId, connection);
                // TODO invoke the listener (later when it is required)
                // also todo the closing of a connection


                return connection;
            } else {
                throw new RuntimeException("No address available");
            }
        } catch (ClosedException closedException) {
            throw closedException;
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            throw new ConnectionIssue();
        }
    }


    public void send(@NonNull Closeable closeable, @NonNull String protocol,
                     @NonNull Connection conn, @NonNull MessageLite message)
            throws InterruptedException, ExecutionException, ClosedException {

        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        QuicChannel quicChannel = (QuicChannel) conn.channel();
        LogUtils.error(TAG, "open " + quicChannel.isOpen());
        LogUtils.error(TAG, "writeable " + quicChannel.isWritable());


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

        QuicChannel quicChannel = (QuicChannel) conn.channel();
        LogUtils.error(TAG, "open " + quicChannel.isOpen());
        LogUtils.error(TAG, "writeable " + quicChannel.isWritable());


        CompletableFuture<MessageLite> ctrl = request(quicChannel, protocol, message);


        while (!ctrl.isDone()) {
            if (closeable.isClosed()) {
                ctrl.cancel(true);
            }
        }

        if (closeable.isClosed()) {
            throw new ClosedException();
        }

        return ctrl.get();
    }


    public CompletableFuture<Void> send(@NonNull QuicChannel quicChannel,
                                        @NonNull String protocol,
                                        @NonNull MessageLite message) {


        LogUtils.error(TAG, protocol);
        CompletableFuture<Void> ret = new CompletableFuture<>();

        try {
            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new SimpleChannelInboundHandler<Object>() {

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                throws Exception {
                            LogUtils.error(TAG, cause);
                            ctx.fireExceptionCaught(cause);
                            ret.completeExceptionally(cause);
                            ctx.close().get();
                        }

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, Object msg)
                                throws Exception {

                            if (msg instanceof String) {
                                String received = (String) msg;
                                LogUtils.error(TAG, "send " + received );
                                if (Objects.equals(received, IPFS.NA)){
                                    throw new ProtocolIssue();
                                } else if (Objects.equals(received, protocol)) {

                                    // clean negotiation stuff
                                    cleanNeoPipeline(ctx);



                                    ctx.pipeline().addFirst(ProtobufVarint32LengthFieldPrepender.class.getSimpleName(),
                                            new ProtobufVarint32LengthFieldPrepender());
                                    ctx.pipeline().addFirst(ProtobufEncoder.class.getSimpleName(),
                                            new ProtobufEncoder());

                                    LogUtils.error(TAG, ctx.pipeline().names().toString());

                                }
                            } else {
                                throw new Exception("not expected state");
                            }

                        }
                    }).sync().get();


            initNeoPipeline(streamChannel);

            LogUtils.error(TAG, streamChannel.pipeline().names().toString());
            streamChannel.write(IPFS.STREAM_PROTOCOL);
            streamChannel.write(protocol);
            ret.complete(streamChannel.writeAndFlush(message).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).get());

        } catch (Throwable throwable) {
            // TODO rethink
            ret.completeExceptionally(throwable);
        }

        return ret;
    }

    private void cleanNeoPipeline(@NonNull ChannelHandlerContext ctx){
        ctx.pipeline().remove(StringSuffixCodec.class.getSimpleName());
        ctx.pipeline().remove(StringEncoder.class.getSimpleName());
        ctx.pipeline().remove(StringDecoder.class.getSimpleName());
        ctx.pipeline().remove(ProtobufVarint32LengthFieldPrepender.class.getSimpleName());
        ctx.pipeline().remove(ProtobufVarint32FrameDecoder.class.getSimpleName());
    }

    private void initNeoPipeline(@NonNull Channel channel){
        channel.pipeline().addFirst(StringSuffixCodec.class.getSimpleName(),
                new StringSuffixCodec('\n'));
        channel.pipeline().addFirst(StringEncoder.class.getSimpleName(),
                new StringEncoder(Charsets.UTF_8));
        channel.pipeline().addFirst(StringDecoder.class.getSimpleName(),
                new StringDecoder(Charsets.UTF_8));
        channel.pipeline().addFirst(ProtobufVarint32LengthFieldPrepender.class.getSimpleName(),
                new ProtobufVarint32LengthFieldPrepender());
        channel.pipeline().addFirst(ProtobufVarint32FrameDecoder.class.getSimpleName(),
                new ProtobufVarint32FrameDecoder());
    }

    public CompletableFuture<MessageLite> request(@NonNull QuicChannel quicChannel,
                                                  @NonNull String protocol,
                                                  @Nullable MessageLite message){

        LogUtils.error(TAG, protocol);
        CompletableFuture<MessageLite> ret = new CompletableFuture<>();

        try {
            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new SimpleChannelInboundHandler<Object>() {

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                throws Exception {
                            LogUtils.error(TAG, cause);
                            ctx.fireExceptionCaught(cause);
                            ret.completeExceptionally(cause);
                            ctx.close();
                        }

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, Object msg)
                                throws Exception {

                             if (msg instanceof String) {
                                String received = (String) msg;
                                LogUtils.error(TAG, "request " + received + " " );

                                if (Objects.equals(received, IPFS.NA)){
                                    throw new ProtocolIssue();
                                } else if (Objects.equals(received, protocol)) {


                                    // clean neogation stuff
                                    cleanNeoPipeline(ctx);



                                    ctx.pipeline().addFirst(ProtobufVarint32LengthFieldPrepender.class.getSimpleName(),
                                            new ProtobufVarint32LengthFieldPrepender());
                                    ctx.pipeline().addFirst(ProtobufEncoder.class.getSimpleName(),
                                            new ProtobufEncoder());


                                    // protocol decoder
                                    ctx.pipeline().addFirst(ProtobufVarint32FrameDecoder.class.getSimpleName(),
                                            new ProtobufVarint32FrameDecoder());


                                    switch (protocol) {
                                        case IPFS.IDENTITY_PROTOCOL:
                                            ctx.pipeline().addFirst(ProtobufDecoder.class.getSimpleName(),
                                                    new ProtobufDecoder(
                                                            IdentifyOuterClass.Identify.getDefaultInstance()));
                                            break;
                                        case IPFS.BITSWAP_PROTOCOL:
                                            ctx.pipeline().addFirst(ProtobufDecoder.class.getSimpleName(),
                                                    new ProtobufDecoder(
                                                            MessageOuterClass.Message.getDefaultInstance()));
                                            break;
                                        case IPFS.KAD_DHT_PROTOCOL:
                                            ctx.pipeline().addFirst(ProtobufDecoder.class.getSimpleName(),
                                                    new ProtobufDecoder(
                                                            Dht.Message.getDefaultInstance()));
                                            break;
                                        default:
                                            throw new RuntimeException("Decoder not defined");
                                    }

                                    if (message != null) {
                                        ctx.write(message);
                                        ctx.flush();
                                    }

                                    LogUtils.error(TAG, ctx.pipeline().names().toString());

                                }
                            } else if (msg instanceof MessageLite) {
                                 LogUtils.error(TAG, "Found " + protocol);
                                 ret.complete((MessageLite) msg);
                                 ctx.close();
                             } else {
                                throw new Exception("not expected");
                            }

                        }
                    }).sync().get();

            initNeoPipeline(streamChannel);



            LogUtils.error(TAG, streamChannel.pipeline().names().toString());

            streamChannel.write(IPFS.STREAM_PROTOCOL);
            streamChannel.write(protocol);
            streamChannel.flush();

        } catch (Throwable throwable) {
            // TODO rethink
            ret.completeExceptionally(throwable);
        }

        return ret;
    }

    private CompletableFuture<Connection> dial(@NonNull PeerId peerId, @NonNull Multiaddr... multiaddrs) {
        CompletableFuture<Connection> connect = new CompletableFuture<>();

        int size = multiaddrs.length;
        for (Multiaddr addr : multiaddrs) {
            size--;
            try {
                LiteConnection liteConnection = new LiteConnection(peerId, dial(addr).get());
                connect.complete(liteConnection);
                break;
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
                if (size == 0) {
                    connect.completeExceptionally(throwable);
                }
            }
        }

        return connect;
    }

    public Future<QuicChannel> dial(@NonNull Multiaddr multiaddr) throws Exception {

        InetAddress inetAddress;
        if (multiaddr.has(Protocol.IP4)) {
            inetAddress = Inet4Address.getByName(multiaddr.getStringComponent(Protocol.IP4));
        } else if (multiaddr.has(Protocol.IP6)) {
            inetAddress = Inet6Address.getByName(multiaddr.getStringComponent(Protocol.IP6));
        } else {
            throw new RuntimeException();
        }
        int port = multiaddr.udpPortFromMultiaddr();

        NioEventLoopGroup group = new NioEventLoopGroup(1);

        ChannelHandler codec = new QuicClientCodecBuilder()
                .sslContext(IPFS.CLIENT_SSL_INSTANCE)
                .maxIdleTimeout(50000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                //.datagram(100000, 100000)
                .build();

        Bootstrap bs = new Bootstrap();
        Channel channel = bs.group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec)
                .bind(0).sync().channel();

        return QuicChannel.newBootstrap(channel)

                .streamHandler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        // As we did not allow any remote initiated streams we will never see this method called.
                        // That said just let us keep it here to demonstrate that this handle would be called
                        // for each remote initiated stream.


                        LogUtils.error(TAG, "Channel active");


                        // ctx.close();
                    }
                })
                .remoteAddress(new InetSocketAddress(inetAddress, port))
                .connect();


    }

    public void start(int port) throws InterruptedException {

        AtomicReference<QuicChannel> channel = new AtomicReference<>();
        ChannelHandler codec = new QuicServerCodecBuilder().sslContext(IPFS.SERVER_SSL_INSTANCE)
                .maxIdleTimeout(50000, TimeUnit.MILLISECONDS)
                // Configure some limits for the maximal number of streams (and the data) that we want to handle.
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                //.datagram(1000000, 1000000)
                // Setup a token handler. In a production system you would want to implement and provide your custom
                // one.
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                // ChannelHandler that is added into QuicChannel pipeline.
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        QuicChannel quicChannel = (QuicChannel) ctx.channel();
                        channel.set(quicChannel);


                        // Create streams etc..
                        LogUtils.info(TAG, "Connection QuicChannel: {} " + channel);
                        //ctx.writeAndFlush(IPFS.MULTISTREAM_PROTO);
                        //ctx.writeAndFlush(IPFS.IDENTITY_PROTOCOL);
                    }

                    public void channelInactive(ChannelHandlerContext ctx) {
                        ((QuicChannel) ctx.channel()).collectStats().addListener(f -> {
                            if (f.isSuccess()) {
                                LogUtils.info(TAG, "Connection closed: {} " + f.getNow());
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

                        initNeoPipeline(ch);

                        ch.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                // TODO rehtink
                                LogUtils.error(TAG, cause);
                                ctx.close();
                                super.exceptionCaught(ctx, cause);
                            }

                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, Object data) throws Exception {

                                if (data instanceof String) {

                                    String msg = (String) data;
                                    LogUtils.error(TAG, "Handle " + msg);
                                    switch (msg) {
                                        case IPFS.STREAM_PROTOCOL:
                                            ctx.writeAndFlush(IPFS.STREAM_PROTOCOL);
                                            break;
                                        case IPFS.IDENTITY_PROTOCOL:

                                            ctx.writeAndFlush(IPFS.IDENTITY_PROTOCOL);

                                            // clean negotiation stuff
                                            cleanNeoPipeline(ctx);


                                            ctx.pipeline().addFirst(ProtobufVarint32LengthFieldPrepender.class.getSimpleName(),
                                                    new ProtobufVarint32LengthFieldPrepender());
                                            ctx.pipeline().addFirst(ProtobufEncoder.class.getSimpleName(),
                                                    new ProtobufEncoder());

                                            try {
                                                Multiaddr multiaddr = new Multiaddr("/ip4/127.0.0.1/udp/5001/quic");


                                                IdentifyOuterClass.Identify response =
                                                        IdentifyOuterClass.Identify.newBuilder()
                                                                .setAgentVersion(IPFS.AGENT)
                                                                // TODO  .setPublicKey()
                                                                .setProtocolVersion(IPFS.PROTOCOL_VERSION)
                                                                .setObservedAddr(ByteString.copyFrom(multiaddr.getBytes()))
                                                                .build();
                                                ctx.writeAndFlush(response).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);

                                            } catch (Throwable throwable) {
                                                LogUtils.error(TAG, throwable);
                                            }
                                            break;
                                        case IPFS.BITSWAP_PROTOCOL:

                                            ctx.writeAndFlush(IPFS.BITSWAP_PROTOCOL);

                                            break;
                                        default:
                                            ctx.writeAndFlush(IPFS.NA).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                                            LogUtils.error(TAG, msg);
                                            //ctx.close(); // TODO rethink
                                            break;
                                    }
                                } else if (data instanceof MessageLite) {
                                    // TODO not working yet
                                    MessageLite messageLite = (MessageLite) data;
                                    PeerId peerId = null; // BIG TODO
                                    // TODO also GatePeer (error)
                                    if (messageLite instanceof MessageOuterClass.Message) {
                                        forwardMessage(peerId, messageLite);
                                    } else {
                                        throw new Exception("illegal state");
                                    }

                                } else {
                                    throw new Exception("illegal state");
                                }
                            }
                        });
                    }
                }).build();

        Bootstrap bs = new Bootstrap();
        server = bs.group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec)
                .bind(new InetSocketAddress(port)).sync().channel();

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
        metrics.protectPeer(peerId);
    }

    public long numConnections() {
        return metrics.numConnections();
    }

    public void trimConnections() {
        metrics.trimConnections();
    }

    public static class LiteConnection implements Connection {
        private final QuicChannel channel;
        private final PeerId peerId;

        public LiteConnection(@NonNull PeerId peerId, @NonNull QuicChannel channel) {
            this.peerId = peerId;
            this.channel = channel;
        }

        @NotNull
        @Override
        public StreamMuxer.Session muxerSession() {
            return null;
        }

        @NotNull
        @Override
        public SecureChannel.Session secureSession() {
            return null;
        }

        @NotNull
        @Override
        public Transport transport() {
            return null;
        }

        @NotNull
        @Override
        public Multiaddr localAddress() {
            return null;
        }

        @NotNull
        @Override
        public Multiaddr remoteAddress() {
            return null;
        }

        @NotNull
        @Override
        public PeerId remoteId() {
            return peerId;
        }

        @NotNull
        @Override
        public Channel channel() {
            return channel;
        }

        @Override
        public boolean isInitiator() {
            return false;
        }

        @Override
        public void pushHandler(@NotNull ChannelHandler handler) {

        }

        @Override
        public void pushHandler(@NotNull String name, @NotNull ChannelHandler handler) {

        }

        @Override
        public void addHandlerBefore(@NotNull String baseName, @NotNull String name, @NotNull ChannelHandler handler) {

        }

        @NotNull
        @Override
        public CompletableFuture<Unit> close() {

            // TODO return new CompletableFuture<Connection>(channel.close().get());
            return null;
        }

        @NotNull
        @Override
        public CompletableFuture<Unit> closeFuture() {
            return null;
        }
    }
}
