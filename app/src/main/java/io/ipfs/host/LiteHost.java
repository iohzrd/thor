package io.ipfs.host;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import io.ipfs.multihash.Multihash;
import io.ipfs.relay.Relay;
import io.ipns.Ipns;
import io.libp2p.core.ConnectionClosedException;
import io.libp2p.core.ConnectionHandler;
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


public class LiteHost implements BitSwapReceiver, BitSwapNetwork {
    private static final String TAG = LiteHost.class.getSimpleName();
    private static final Duration DefaultRecordEOL = Duration.ofHours(24);

    private final NioEventLoopGroup group = new NioEventLoopGroup(1);
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

    @Nullable
    private Channel client;

    public LiteHost(@NonNull PrivKey privKey, @NonNull BlockStore blockstore, int port, int alpha) {

        this.privKey = privKey;
        this.metrics = new ConnectionManager(this, IPFS.LOW_WATER, IPFS.HIGH_WATER, IPFS.GRACE_PERIOD);

        this.routing = new KadDHT(this,
                new Ipns(), alpha, IPFS.KAD_DHT_BETA,
                IPFS.KAD_DHT_BUCKET_SIZE);

        this.exchange = BitSwap.create(this, blockstore);
        this.relay = new Relay(this);


        ChannelHandler codec = new QuicClientCodecBuilder()
                .sslContext(IPFS.CLIENT_SSL_INSTANCE)
                .maxIdleTimeout(30, TimeUnit.SECONDS)
                .initialMaxData(1000000000)
                .initialMaxStreamDataBidirectionalLocal(100000000)
                .initialMaxStreamDataBidirectionalRemote(100000000)
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
                send(closeable, IPFS.BITSWAP_PROTOCOL, con, message.ToProtoV1());
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

                    connections.put(peerId, connection);
                    // TODO invoke the listener (later when it is required)
                    // also todo the closing of a connection


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

        QuicChannel quicChannel = (QuicChannel) conn.channel();


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

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                throws Exception {
                            LogUtils.error(TAG, cause.getClass().getSimpleName());
                            ret.completeExceptionally(cause);
                            ctx.close().get();
                        }

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, Object value)
                                throws Exception {

                            ByteBuf msg = (ByteBuf) value;
                            Objects.requireNonNull(msg);

                            byte[] data = new byte[msg.readableBytes()];
                            int readerIndex = msg.readerIndex();
                            msg.getBytes(readerIndex, data);
                            List<String> tokens = tokens(data);
                            for (String received : tokens) {
                                LogUtils.error(TAG, "send " + received);
                                if (Objects.equals(received, IPFS.NA)) {
                                    throw new ProtocolIssue();
                                } else if (Objects.equals(received, IPFS.STREAM_PROTOCOL)) {
                                    // ignore
                                } else if (Objects.equals(received, protocol)) {
                                    LogUtils.error(TAG, ctx.pipeline().names().toString());

                                    ret.complete(ctx.writeAndFlush(
                                            encode(message)).addListener(
                                            (ChannelFutureListener) future -> ctx.close()).get());
                                } else {
                                    throw new ProtocolIssue();
                                }
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


    public CompletableFuture<MessageLite> request(@NonNull QuicChannel quicChannel,
                                                  @NonNull String protocol,
                                                  @Nullable MessageLite message){

        LogUtils.error(TAG, protocol);
        CompletableFuture<MessageLite> ret = new CompletableFuture<>();
        CompletableFuture<Void> activation = new CompletableFuture<>();

        try {
            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new SimpleChannelInboundHandler<Object>() {

                        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        private long expectedLength;
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
                        protected void channelRead0(ChannelHandlerContext ctx, Object value)
                                throws Exception {

                            ByteBuf msg = (ByteBuf) value;
                            Objects.requireNonNull(msg);

                            if (negotiation) {
                                byte[] data = new byte[msg.readableBytes()];
                                int readerIndex = msg.readerIndex();
                                msg.getBytes(readerIndex, data);
                                List<String> tokens = tokens(data);
                                for (String received : tokens) {
                                    LogUtils.error(TAG, "send " + received);
                                    if (Objects.equals(received, IPFS.NA)) {
                                        throw new ProtocolIssue();
                                    } else if (Objects.equals(received, IPFS.STREAM_PROTOCOL)) {
                                        // ignore
                                    } else if (Objects.equals(received, protocol)) {
                                        negotiation = false;
                                        LogUtils.error(TAG, ctx.pipeline().names().toString());
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
                            } else {

                                if (buffer.size() > 0) {
                                    msg.readBytes(buffer, msg.readableBytes());
                                } else {
                                    byte[] data = new byte[msg.readableBytes()];
                                    int readerIndex = msg.readerIndex();
                                    msg.getBytes(readerIndex, data);
                                    try (InputStream inputStream = new ByteArrayInputStream(data)) {
                                        expectedLength = Multihash.readVarint(inputStream);
                                        copy(inputStream, buffer);
                                    }
                                }

                                if (buffer.size() == expectedLength) {

                                    switch (protocol) {
                                        case IPFS.IDENTITY_PROTOCOL:
                                            LogUtils.error(TAG + "FFFF", "Found " + protocol);
                                            ret.complete(IdentifyOuterClass.Identify.parseFrom(buffer.toByteArray()));
                                            break;
                                        case IPFS.BITSWAP_PROTOCOL:
                                            LogUtils.error(TAG + "FFFF", "Found " + protocol);
                                            ret.complete(MessageOuterClass.Message.parseFrom(buffer.toByteArray()));
                                            break;
                                        case IPFS.KAD_DHT_PROTOCOL:
                                            LogUtils.error(TAG + "FFFF", "Found " + protocol);
                                            ret.complete(Dht.Message.parseFrom(buffer.toByteArray()));
                                            break;
                                        default:
                                            throw new Exception("unknown protocol");
                                    }

                                    buffer.close();
                                    buffer.reset();
                                    ctx.close();

                                } else {
                                    LogUtils.error(TAG, "iteration " + buffer.size());
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

        // TODO close stream

        return activation.thenCompose(s->ret);
    }


    public Promise<QuicChannel> dial(@NonNull Multiaddr multiaddr, @Nullable PeerId peerId) throws Exception {

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


                        if (quicChannel.remoteAddress().streamId() > -1000) {

                            //initNeoPipeline(quicChannel);


                            quicChannel.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {

                                private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                                private long expectedLength;
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

                                    boolean firstRun = true;
                                    if (buffer.size() > 0) {
                                        firstRun = false;
                                        msg.readBytes(buffer, msg.readableBytes());
                                    } else {
                                        byte[] data = new byte[msg.readableBytes()];
                                        int readerIndex = msg.readerIndex();
                                        msg.getBytes(readerIndex, data);
                                        try (InputStream inputStream = new ByteArrayInputStream(data)) {
                                            expectedLength = Multihash.readVarint(inputStream);
                                            copy(inputStream, buffer);
                                        }
                                    }

                                    LogUtils.error(TAG + "CLIENT", ctx.pipeline().names().toString());
                                    LogUtils.error(TAG + "CLIENT", "Buffer Size " + buffer.size() + " ex " + expectedLength);
                                    int bufferSize = buffer.size();
                                    if ((bufferSize > expectedLength || bufferSize == expectedLength) && firstRun) {
                                        List<String> tokens = tokens(buffer);
                                        for (String token : tokens) {
                                            LogUtils.error(TAG + "CLIENT", "TOKEN " + token);
                                            if (token.endsWith(IPFS.STREAM_PROTOCOL)) {
                                                ctx.writeAndFlush(writeToken(IPFS.STREAM_PROTOCOL));
                                            } else if (token.endsWith(IPFS.BITSWAP_PROTOCOL)) {
                                                lastProtocol = IPFS.BITSWAP_PROTOCOL;
                                                ctx.writeAndFlush(writeToken(IPFS.BITSWAP_PROTOCOL)).get();
                                            } else if (token.endsWith(IPFS.IDENTITY_PROTOCOL)) {
                                                //lastProtocol = IPFS.IDENTITY_PROTOCOL;
                                                ctx.writeAndFlush(writeToken(IPFS.IDENTITY_PROTOCOL)).get();

                                                try {
                                                    Multiaddr multiaddr = new Multiaddr("/ip4/127.0.0.1/udp/5001/quic");

                                                    IdentifyOuterClass.Identify response =
                                                            IdentifyOuterClass.Identify.newBuilder()
                                                                    .setAgentVersion(IPFS.AGENT)
                                                                    // TODO  .setPublicKey()
                                                                    .setProtocolVersion(IPFS.PROTOCOL_VERSION)
                                                                    .setObservedAddr(ByteString.copyFrom(multiaddr.getBytes()))
                                                                    .build();
                                                    ctx.writeAndFlush(encode(response)).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                                                } catch (Throwable throwable) {
                                                    LogUtils.error(TAG, throwable);
                                                }


                                            }
                                        }
                                    } else if (buffer.size() == expectedLength) {

                                        try {
                                            //PeerId peerId = null; // BIG TODO
                                            switch (lastProtocol) {
                                                case IPFS.IDENTITY_PROTOCOL:
                                                    IdentifyOuterClass.Identify messsage = IdentifyOuterClass.Identify.parseFrom(buffer.toByteArray());
                                                    LogUtils.error(TAG + "CLIENT", "Success Server " + messsage.getAgentVersion());
                                                    break;
                                                case IPFS.KAD_DHT_PROTOCOL:
                                                    Dht.Message message = Dht.Message.parseFrom(buffer.toByteArray());
                                                    LogUtils.error(TAG + "CLIENT", IPFS.KAD_DHT_PROTOCOL);
                                                    forwardMessage(peerId, message);
                                                    break;
                                                default:
                                                    LogUtils.error(TAG + "CLIENT", new String(buffer.toByteArray()));
                                                    // throw an exception here
                                            }

                                        } catch (Throwable throwable) {
                                            LogUtils.error(TAG + "CLIENT", new String(buffer.toByteArray()));
                                            LogUtils.error(TAG + "CLIENT", throwable);
                                        }
                                        buffer.close();
                                        buffer.reset();

                                        ctx.close(); // TODO rethink

                                    } else {
                                        // LogUtils.error(TAG, lastProtocol);
                                        // LogUtils.error(TAG, "Read : " + new String(buffer.toByteArray()));
                                    }

                                }
                            });


                            // ctx.close();
                        }
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

    public List<String> tokens(@NonNull ByteArrayOutputStream buffer) {
        return tokens(buffer.toByteArray());
    }

    public List<String> tokens(@NonNull byte[] data) {
        String tags = new String(data, Charsets.UTF_8);
        List<String> list = new ArrayList<>();
        String[] result = tags.split("\n");
        for (String item : result) {
            if (item.startsWith("/") || item.equals(IPFS.NA)) {
                list.add(item);
            } else {
                try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    try (ByteArrayInputStream in = new ByteArrayInputStream(item.getBytes(Charsets.UTF_8))) {
                        Multihash.readVarint(in);
                        copy(in, out);
                        list.add(new String(out.toByteArray(), Charsets.UTF_8));
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
            }
        }
        return list;
    }

    public long copy(InputStream source, OutputStream sink) throws IOException {
        long nread = 0L;
        byte[] buf = new byte[4096];
        int n;
        while ((n = source.read(buf)) > 0) {
            sink.write(buf, 0, n);
            nread += n;
        }
        return nread;
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

        // BIG TODO
        @NotNull
        @Override
        public Multiaddr remoteAddress() {
            throw new RuntimeException("TODO");
            // return null;
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
