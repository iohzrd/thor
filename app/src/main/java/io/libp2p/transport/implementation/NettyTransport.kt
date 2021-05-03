package io.libp2p.transport.implementation

import io.LogUtils
import io.ipfs.IPFS
import io.ipfs.multibase.Charsets
import io.libp2p.core.Connection
import io.libp2p.core.ConnectionHandler
import io.libp2p.core.Libp2pException
import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import io.libp2p.core.multiformats.MultiaddrDns
import io.libp2p.core.multiformats.Protocol
import io.libp2p.core.transport.Transport
import io.libp2p.etc.REMOTE_PEER_ID
import io.libp2p.etc.types.lazyVar
import io.libp2p.etc.types.toVoidCompletableFuture
import io.libp2p.etc.util.netty.StringSuffixCodec
import io.libp2p.etc.util.netty.nettyInitializer
import io.libp2p.transport.ConnectionUpgrader
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LineBasedFrameDecoder
import io.netty.handler.codec.protobuf.ProtobufEncoder
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import io.netty.incubator.codec.quic.*
import io.netty.util.CharsetUtil
import io.netty.util.concurrent.Future
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

abstract class NettyTransport(
        private val upgrader: ConnectionUpgrader
) : Transport {
    private var closed = false
    var connectTimeout = Duration.ofSeconds(150)// TODO

    private val listeners = mutableMapOf<Multiaddr, Channel>()
    private val channels = mutableListOf<Channel>()

    private var workerGroup by lazyVar { NioEventLoopGroup() }
    private var bossGroup by lazyVar { NioEventLoopGroup(1) }

    private var client by lazyVar {
        Bootstrap().apply {
            group(workerGroup)
            channel(NioSocketChannel::class.java)
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout.toMillis().toInt())
        }
    }

    private var server by lazyVar {
        ServerBootstrap().apply {
            group(bossGroup, workerGroup)
            channel(NioServerSocketChannel::class.java)
        }
    }

    override val activeListeners: Int
        get() = listeners.size
    override val activeConnections: Int
        get() = channels.size

    override fun listenAddresses(): List<Multiaddr> {
        return listeners.values.map {
            toMultiaddr(it.localAddress() as InetSocketAddress)
        }
    }

    override fun initialize() {
    }

    override fun close(): CompletableFuture<Unit> {
        closed = true

        val unbindsCompleted = listeners
                .map { (_, ch) -> ch }
                .map { it.close().toVoidCompletableFuture() }

        val channelsClosed = channels
                .toMutableList() // need a copy to avoid potential co-modification problems
                .map { it.close().toVoidCompletableFuture() }

        val everythingThatNeedsToClose = unbindsCompleted.union(channelsClosed)
        val allClosed = CompletableFuture.allOf(*everythingThatNeedsToClose.toTypedArray())

        return allClosed.thenApply {
            workerGroup.shutdownGracefully()
            bossGroup.shutdownGracefully()
            Unit
        }
    } // close

    override fun listen(addr: Multiaddr, connHandler: ConnectionHandler): CompletableFuture<Unit> {
        if (closed) throw Libp2pException("Transport is closed")


        val codec = QuicServerCodecBuilder().sslContext(IPFS.SERVER_SSL_INSTANCE)
                .maxIdleTimeout(50000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                .datagram(100000, 100000)
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE) // ChannelHandler that is added into QuicChannel pipeline.
                .handler(object : ChannelInboundHandlerAdapter() {
                    override fun channelActive(ctx: ChannelHandlerContext) {
                        val channel = ctx.channel() as QuicChannel

                        LogUtils.error("TAG", "fdasas")
                        // Create streams etc..
                    }

                    override fun channelInactive(ctx: ChannelHandlerContext) {
                        (ctx.channel() as QuicChannel).collectStats().addListener { f: Future<in QuicConnectionStats?> ->
                            if (f.isSuccess) {
                                LogUtils.info("QuicServer.TAG", "Connection closed: {} " + f.now)
                            }
                        }
                    }

                    override fun isSharable(): Boolean {
                        return true
                    }
                }).streamHandler(object : ChannelInitializer<QuicStreamChannel>() {
                    override fun initChannel(ch: QuicStreamChannel) {

                        // Add a LineBasedFrameDecoder here as we just want to do some simple HTTP 0.9 handling.
                        ch.pipeline().addLast(LineBasedFrameDecoder(1024))
                                .addLast(object : ChannelInboundHandlerAdapter() {
                                    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                                        val dsa = ctx.channel()
                                        val byteBuf = msg as ByteBuf
                                        try {
                                            if (byteBuf.toString(CharsetUtil.US_ASCII).trim { it <= ' ' } == "GET /") {
                                                val buffer = ctx.alloc().directBuffer()
                                                buffer.writeCharSequence("Hello World!\r\n", CharsetUtil.US_ASCII)
                                                // Write the buffer and shutdown the output by writing a FIN.
                                                ctx.writeAndFlush(buffer).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
                                            }
                                        } finally {
                                            byteBuf.release()
                                        }
                                    }
                                })
                    }
                }).build()

        val connectionBuilder = makeConnectionBuilder(connHandler, false)
        val channelHandler = serverTransportBuilder(connectionBuilder, addr) ?: connectionBuilder

        val listener = server.clone().handler(codec)
                .childHandler(
                        nettyInitializer { init ->
                            registerChannel(init.channel)
                            //init.addLastLocal(channelHandler)
                        }
                )

        val bindComplete = listener.bind(fromMultiaddr(addr))

        bindComplete.also {
            synchronized(this@NettyTransport) {
                listeners += addr to it.channel()
                it.channel().closeFuture().addListener {
                    synchronized(this@NettyTransport) {
                        listeners -= addr
                    }
                }
            }
        }

        return bindComplete.toVoidCompletableFuture()
    } // listener

    protected abstract fun serverTransportBuilder(
            connectionBuilder: ConnectionBuilder,
            addr: Multiaddr
    ): ChannelHandler?

    override fun unlisten(addr: Multiaddr): CompletableFuture<Unit> {
        return listeners[addr]?.close()?.toVoidCompletableFuture()
                ?: throw Libp2pException("No listeners on address $addr")
    } // unlisten

    override fun dial(addr: Multiaddr, connHandler: ConnectionHandler):
            CompletableFuture<Connection> {
        if (closed) throw Libp2pException("Transport is closed")

        val codec = QuicClientCodecBuilder()
                .sslContext(IPFS.CLIENT_SSL_INSTANCE)
                .maxIdleTimeout(50000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                .datagram(100000, 100000)
                .build()
        val bs = Bootstrap()
        val channel = bs.group(workerGroup)
                .channel(NioDatagramChannel::class.java)
                .handler(codec)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout.toMillis().toInt())
                .bind(0).sync().channel()


        val remotePeerId = addr.getStringComponent(Protocol.P2P)?.let { PeerId.fromBase58(it) }
        //val connectionBuilder = makeConnectionBuilder(connHandler, true, remotePeerId)
        //val channelHandler = clientTransportBuilder(connectionBuilder, addr) ?: connectionBuilder

        var ret = CompletableFuture<String>()


        var quicChannel = QuicChannel.newBootstrap(channel)
                .streamHandler(object : ChannelInboundHandlerAdapter() {

                    @Throws(Exception::class)
                    override fun channelActive(ctx: ChannelHandlerContext) {
                        // As we did not allow any remote initiated streams we will never see this method called.
                        // That said just let us keep it here to demonstrate that this handle would be called
                        // for each remote initiated stream.
                        LogUtils.error("TAG", "Channel active")



                        ctx.flush()
                        // ctx.close()
                    }

                    @Throws(java.lang.Exception::class)
                    override fun channelRead(ctx: ChannelHandlerContext, msg: Any?) {

                        val byteBuf = msg as ByteBuf
                        try {


                        } finally {
                            byteBuf.release()
                        }


                    }
                })
                .remoteAddress(fromMultiaddr(addr))
                .connect().get()

        ret.get()
        ret = CompletableFuture<String>()


        val streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                object : SimpleChannelInboundHandler<Any>() {
                    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) { // (4)
                        // Close the connection when an exception is raised.
                        LogUtils.error("TAG", cause)
                        ctx.close()
                    }

                    /*
                    @Throws(java.lang.Exception::class)
                    override fun channelActive(ctx: ChannelHandlerContext) {

                        ctx.write(Varint32LengthField.encode(
                                ByteBufUtil.encodeString(ctx.alloc(), CharBuffer.wrap("/ipfs/id/1.0.0" + "\""), Charsets.UTF_8)))
                        //ctx.writeAndFlush("/ipfs/id/1.0.0");
                        ctx.flush()
                    }*/

                    @Throws(java.lang.Exception::class)
                    override fun channelRead0(ctx: ChannelHandlerContext, msg: Any) {
                        try {
                            LogUtils.error("TAG", msg.toString())
                            //val protos = ProtocolSplitter.protocols(msg!!)

                        } catch (throwable: Throwable) {
                            ret.completeExceptionally(throwable)
                            throw throwable
                        }
                    }
                }).get()

        streamChannel.pipeline().addFirst(ProtobufEncoder(),
                ProtobufVarint32FrameDecoder(),
                ProtobufVarint32LengthFieldPrepender(),
                StringDecoder(Charsets.UTF_8),
                StringEncoder(Charsets.UTF_8),
                StringSuffixCodec('\n'))


        LogUtils.error("TAG", streamChannel.pipeline().names().toString())

        streamChannel.write("/ipfs/id/1.0.0")
        var token = ret.get()
        LogUtils.error("TAG", token)


        Thread.sleep(5000)
        streamChannel.write("/ipfs/id/1.0.0")
        token = ret.get()
        LogUtils.error("TAG", token)
        Thread.sleep(5000)
        val connectionEstablished = CompletableFuture<Connection>()


        LogUtils.error("TAG", "QUIC channel 2")

        val connection = ConnectionOverNetty(quicChannel, this, true)
        remotePeerId?.also { quicChannel.attr(REMOTE_PEER_ID).set(it) }
        registerChannel(quicChannel)
        connectionEstablished.complete(connection)


        LogUtils.error("TAG", "QUIC channel 3")
        return connectionEstablished
    } // dial

    protected abstract fun clientTransportBuilder(
            connectionBuilder: ConnectionBuilder,
            addr: Multiaddr
    ): ChannelHandler?

    private fun registerChannel(ch: Channel) {
        if (closed) {
            ch.close()
            return
        }

        synchronized(this@NettyTransport) {
            channels += ch
            ch.closeFuture().addListener {
                synchronized(this@NettyTransport) {
                    channels -= ch
                }
            }
        }
    } // registerChannel

    private fun makeConnectionBuilder(
            connHandler: ConnectionHandler,
            initiator: Boolean,
            remotePeerId: PeerId? = null
    ) = ConnectionBuilder(
            this,
            upgrader,
            connHandler,
            initiator,
            remotePeerId
    )

    protected fun handlesHost(addr: Multiaddr) =
            addr.hasAny(Protocol.IP4, Protocol.IP6, Protocol.DNS4, Protocol.DNS6, Protocol.DNSADDR)

    protected fun hostFromMultiaddr(addr: Multiaddr): String {
        val resolvedAddresses = MultiaddrDns.resolve(addr)
        if (resolvedAddresses.isEmpty())
            throw Libp2pException("Could not resolve $addr to an IP address")

        return resolvedAddresses[0].filterStringComponents().find {
            it.first in arrayOf(Protocol.IP4, Protocol.IP6)
        }?.second ?: throw Libp2pException("Missing IP4/IP6 in multiaddress $addr")
    }

    protected fun portFromMultiaddr(addr: Multiaddr) =
            addr.filterStringComponents().find { p -> p.first == Protocol.UDP }
                    ?.second?.toInt() ?: throw Libp2pException("Missing UDP in multiaddress $addr")

    private fun fromMultiaddr(addr: Multiaddr): InetSocketAddress {
        val host = hostFromMultiaddr(addr)
        val port = portFromMultiaddr(addr)
        return InetSocketAddress(host, port)
    } // fromMultiaddr

    abstract fun toMultiaddr(addr: InetSocketAddress): Multiaddr
} // class NettyTransportBase
