package io.ipfs.host;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.collect.Iterables;
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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BinaryOperator;

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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
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
import io.netty.util.concurrent.Promise;
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
                send(closeable, IPFS.BITSWAP_PROTOCOL, con, message.ToProtoV1());
                LogUtils.error(TAG, "success send writing ");
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

                    boolean value = future.isCancellable();

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

                    LogUtils.error(TAG, "Success CONN 1" + addrInfo.toString());
                    QuicChannel quic = future.get();
                    LogUtils.error(TAG, "Success CONN 2 " + addrInfo.toString());
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

        MessageLite res = ctrl.get();
        LogUtils.error(TAG, "success request  " + res.getClass().getSimpleName());
        return res;
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
                            ret.completeExceptionally(cause);
                            ctx.close();
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

                                    LogUtils.error(TAG, ctx.pipeline().names().toString());
                                    ret.complete(ctx.writeAndFlush(message).addListener(
                                            (ChannelFutureListener) future -> ctx.close()).get());
                                }
                            } else {
                                throw new Exception("not expected state");
                            }

                        }
                    }).sync().get( IPFS.TIMEOUT_SEND, TimeUnit.SECONDS);


            initNeoPipeline(streamChannel);


            streamChannel.pipeline().addFirst(ProtobufEncoder.class.getSimpleName(),
                    new ProtobufEncoder());


            LogUtils.error(TAG, streamChannel.pipeline().names().toString());
            streamChannel.writeAndFlush(IPFS.STREAM_PROTOCOL);
            streamChannel.writeAndFlush(protocol);


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
        CompletableFuture<Void> activation = new CompletableFuture<>();

        try {
            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new SimpleChannelInboundHandler<Object>() {

                        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        private long expectedLength;

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

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                throws Exception {
                            LogUtils.error(TAG, cause);
                            ret.completeExceptionally(cause);
                            activation.completeExceptionally(cause);
                            ctx.close();
                        }

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, Object msg)
                                throws Exception {

                            LogUtils.error(TAG, msg.getClass().getSimpleName());
                             if (msg instanceof String) {
                                String received = (String) msg;
                                LogUtils.error(TAG, "request " + received + " " );

                                if (Objects.equals(received, IPFS.NA)){
                                    throw new ProtocolIssue();
                                } else if (Objects.equals(received, protocol)) {


                                    // clean neogation stuff
                                    cleanNeoPipeline(ctx);



                                    // protocol decoder

                                    /*
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
                                    }*/

                                    /*
                                    ctx.pipeline().addFirst(ProtobufVarint32LengthFieldPrepender.class.getSimpleName(),
                                            new ProtobufVarint32LengthFieldPrepender());
                                    ctx.pipeline().addFirst(ProtobufEncoder.class.getSimpleName(),
                                            new ProtobufEncoder());*/


                                    LogUtils.error(TAG, ctx.pipeline().names().toString());
                                    if (message != null) {

                                        LogUtils.error(TAG, "Now write the message");
                                        LogUtils.error(TAG, ctx.pipeline().names().toString());

                                        byte[] data = message.toByteArray();
                                        try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                                            Multihash.putUvarint(buf, data.length);
                                            buf.write(data);
                                            activation.complete(
                                                    ctx.writeAndFlush(Unpooled.buffer().writeBytes(buf.toByteArray()))
                                                            .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).get());
                                        } catch (Throwable throwable) {
                                            LogUtils.error(TAG, throwable);
                                        }

                                       // ctx.writeAndFlush(message).get();
                                    } else {
                                        activation.complete(null);
                                    }
                                }
                            } else if (msg instanceof ByteBuf) {

                                 LogUtils.error(TAG, ctx.pipeline().names().toString());

                                 ByteBuf byteBuf = (ByteBuf) msg;
                                 if (buffer.size() > 0) {
                                     byteBuf.readBytes(buffer, byteBuf.readableBytes());
                                 } else {
                                     byte[] data = new byte[byteBuf.readableBytes()];
                                     int readerIndex = byteBuf.readerIndex();
                                     byteBuf.getBytes(readerIndex, data);
                                     try (InputStream inputStream = new ByteArrayInputStream(data)) {
                                         expectedLength = Multihash.readVarint(inputStream);
                                         copy(inputStream, buffer);
                                     }
                                 }

                                 LogUtils.error(TAG, "Buffer Size " + buffer.size() + " ex " + expectedLength);
                                 if (buffer.size() == expectedLength) {

                                     switch (protocol) {
                                         case IPFS.IDENTITY_PROTOCOL:
                                             LogUtils.error(TAG + "FFFF", "Found " + protocol);
                                             ret.complete(IdentifyOuterClass.Identify.parseFrom(buffer.toByteArray()));
                                             break;
                                         case IPFS.BITSWAP_PROTOCOL:
                                             LogUtils.error(TAG + "FFFF", "Found " + protocol);
                                             ret.complete( MessageOuterClass.Message.parseFrom(buffer.toByteArray()));
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
                                            // ctx.close();

                                 } else {
                                     // LogUtils.error(TAG, lastProtocol);
                                     // LogUtils.error(TAG, "Read : " + new String(buffer.toByteArray()));
                                 }

                                 //ctx.close();
                             } else {
                                throw new Exception("not expected");
                             }

                        }
                    }).sync().get(IPFS.TIMEOUT_REQUEST, TimeUnit.SECONDS);

            initNeoPipeline(streamChannel);


            LogUtils.error(TAG, streamChannel.pipeline().names().toString());

            streamChannel.writeAndFlush(IPFS.STREAM_PROTOCOL);
            streamChannel.writeAndFlush(protocol);

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            // TODO rethink
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

        NioEventLoopGroup group = new NioEventLoopGroup(1);

        ChannelHandler codec = new QuicClientCodecBuilder()
                .sslContext(IPFS.CLIENT_SSL_INSTANCE)
                .maxIdleTimeout(10, TimeUnit.SECONDS)
                .initialMaxData(1000000000)
                .initialMaxStreamDataBidirectionalLocal(100000000)
                .initialMaxStreamDataBidirectionalRemote(100000000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                //.datagram(10000000, 10000000)
                .build();

        Bootstrap bs = new Bootstrap();
        Channel channel = bs.group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec)
                .bind(0).sync().channel();

        return (Promise<QuicChannel>) QuicChannel.newBootstrap(channel)

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

                            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                            private long expectedLength;
                            private String lastProtocol;
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

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                LogUtils.error(TAG, cause);
                                super.exceptionCaught(ctx, cause);
                            }



                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, Object value) throws Exception {

                                if (value instanceof String) {

                                    String msg = (String) value;
                                    LogUtils.error(TAG, "Handle " + msg);
                                    lastProtocol = msg;
                                    switch (msg) {
                                        case IPFS.STREAM_PROTOCOL:
                                            ctx.writeAndFlush(IPFS.STREAM_PROTOCOL);
                                            break;
                                        case IPFS.IDENTITY_PROTOCOL:

                                            ctx.writeAndFlush(IPFS.IDENTITY_PROTOCOL);
                                            // clean negotiation stuff
                                            cleanNeoPipeline(ctx);

                                            // protocol decoder


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


                                                QuicStreamChannel stream = channel.get().createStream(QuicStreamType.BIDIRECTIONAL, new ChannelHandler() {
                                                    @Override
                                                    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                                                        LogUtils.error(TAG, "added");

                                                    }

                                                    @Override
                                                    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
                                                        LogUtils.error(TAG, "re");
                                                    }

                                                    @Override
                                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                                        LogUtils.error(TAG, "cvd");
                                                    }
                                                }).get();
                                                stream.writeAndFlush(response).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);

                                            } catch (Throwable throwable) {
                                                LogUtils.error(TAG, throwable);
                                            }


                                            break;
                                        case IPFS.BITSWAP_PROTOCOL:
                                            ctx.writeAndFlush(IPFS.BITSWAP_PROTOCOL);
                                            break;
                                        case IPFS.KAD_DHT_PROTOCOL:
                                            ctx.writeAndFlush(IPFS.KAD_DHT_PROTOCOL);
                                            break;
                                        default:
                                            LogUtils.error(TAG, IPFS.NA);
                                            ctx.writeAndFlush(IPFS.NA).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                                            break;
                                    }
                                } else if (value instanceof ByteBuf) {


                                    LogUtils.error(TAG, ctx.pipeline().names().toString());

                                    ByteBuf msg = (ByteBuf) value;
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

                                    LogUtils.error(TAG, "Buffer Size " + buffer.size() + " ex " + expectedLength);
                                    if (buffer.size() == expectedLength) {
                                        PeerId peerId = null; // BIG TODO
                                        switch (lastProtocol) {
                                            case IPFS.IDENTITY_PROTOCOL:
                                                IdentifyOuterClass.Identify messsage = IdentifyOuterClass.Identify.parseFrom(buffer.toByteArray());
                                                LogUtils.error(TAG, "Success Server " + messsage.getAgentVersion());
                                                break;
                                            case IPFS.KAD_DHT_PROTOCOL:
                                                Dht.Message message = Dht.Message.parseFrom(buffer.toByteArray());
                                                forwardMessage(peerId, message);
                                                break;
                                            default:
                                                LogUtils.error(TAG, new String(buffer.toByteArray()));
                                                // throw an exception here
                                        }
                                        buffer.close();
                                        buffer.reset();

                                        ctx.close(); // TODO rethink

                                    } else {
                                       // LogUtils.error(TAG, lastProtocol);
                                       // LogUtils.error(TAG, "Read : " + new String(buffer.toByteArray()));
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
