package io.ipfs;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.protobuf.MessageLite;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import identify.pb.IdentifyOuterClass;
import io.LogUtils;
import io.libp2p.core.multiformats.Multiaddr;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsQuicTest {


    private static final String TAG = IpfsQuicTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    //@Test
    public void test_1() throws Exception {

        IPFS ipfs = TestEnv.getTestInstance(context);

        Multiaddr multiaddr = new Multiaddr("/ip4/147.75.109.213/udp/4001/quic/p2p/QmNnooDu7bfjPFoTZYxMNLWUQJyrVwtbZg5gBMjTezGAJN");

        Future<QuicChannel> conn = ipfs.getHost().dial(multiaddr);
        assertNotNull(conn);
        QuicChannel channel = conn.get();
        assertNotNull(channel);




    }

    //@Test
    public void test_2() throws Exception {
        QuicServerExample serverExample = new QuicServerExample();
        serverExample.main();

        QuicClientExample clientExample = new QuicClientExample();
        clientExample.main();

    }

    @Test
    public void test_3() throws Exception {

        IPFS ipfs = TestEnv.getTestInstance(context);
        int port = ipfs.getPort();

        Multiaddr multiaddr = new Multiaddr("/ip4/0.0.0.0/udp/" + port+ "/quic");

        Future<QuicChannel> future = ipfs.getHost().dial(multiaddr);

        QuicChannel quicChannel = future.get();

        CompletableFuture<MessageLite> ret = ipfs.getHost().request(
                quicChannel, IPFS.IDENTITY_PROTOCOL, null);

        MessageLite message = ret.get();
        assertNotNull(message);
        IdentifyOuterClass.Identify identify = (IdentifyOuterClass.Identify) message;

        assertNotNull(identify);
        assertEquals(identify.getAgentVersion(), IPFS.AGENT);

        ret = ipfs.getHost().request(
                quicChannel, IPFS.IDENTITY_PROTOCOL, null);

        message = ret.get();
        assertNotNull(message);
        identify = (IdentifyOuterClass.Identify) message;

        assertNotNull(identify);
        assertEquals(identify.getAgentVersion(), IPFS.AGENT);
    }

    public final class QuicServerExample {


        public  void main() throws Exception {


            ChannelHandler codec = new QuicServerCodecBuilder().sslContext(IPFS.SERVER_SSL_INSTANCE)
                    .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                    // Configure some limits for the maximal number of streams (and the data) that we want to handle.
                    .initialMaxData(10000000)
                    .initialMaxStreamDataBidirectionalLocal(1000000)
                    .initialMaxStreamDataBidirectionalRemote(1000000)
                    .initialMaxStreamsBidirectional(100)
                    .initialMaxStreamsUnidirectional(100)
                    .datagram(10000, 10000)

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
                        protected void initChannel(QuicStreamChannel ch)  {
                            // Add a LineBasedFrameDecoder here as we just want to do some simple HTTP 0.9 handling.
                            ch.pipeline().addLast(new LineBasedFrameDecoder(1024))
                                    .addLast(new ChannelInboundHandlerAdapter() {
                                        @Override
                                        public void channelRead(ChannelHandlerContext ctx, Object obj) {
                                            ByteBuf byteBuf = (ByteBuf) obj;
                                            try {
                                                if (byteBuf.toString(CharsetUtil.US_ASCII).trim().equals("GET /")) {
                                                    ByteBuf buffer = ctx.alloc().directBuffer();
                                                    buffer.writeCharSequence("Hello World!\r\n", CharsetUtil.US_ASCII);
                                                    // Write the buffer and shutdown the output by writing a FIN.
                                                    ctx.writeAndFlush(buffer).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                                                }
                                            } finally {
                                                byteBuf.release();
                                            }
                                        }
                                    });
                        }
                    }).build();
            try {
                Bootstrap bs = new Bootstrap();
                NioEventLoopGroup group = new NioEventLoopGroup(1);
                Channel channel = bs.group(group)
                        .channel(NioDatagramChannel.class)
                        .handler(codec)
                        .bind(new InetSocketAddress(9999)).sync().channel();
                //channel.closeFuture().sync();



            } finally {
               // group.shutdownGracefully();
            }
        }
    }


    public final class QuicClientExample {


        public void main() throws Exception {


            NioEventLoopGroup group = new NioEventLoopGroup(1);
            try {
                ChannelHandler codec = new QuicClientCodecBuilder()
                        .sslContext(IPFS.CLIENT_SSL_INSTANCE)
                        .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                        .initialMaxData(10000000)
                        .initialMaxStreamDataBidirectionalLocal(1000000)
                        .initialMaxStreamDataBidirectionalRemote(1000000)
                        .initialMaxStreamsBidirectional(100)
                        .initialMaxStreamsUnidirectional(100)
                        .datagram(10000, 10000)

                        .build();

                Bootstrap bs = new Bootstrap();
                Channel channel = bs.group(group)
                        .channel(NioDatagramChannel.class)
                        .handler(codec)
                        .remoteAddress(new InetSocketAddress(NetUtil.LOCALHOST4, 9999))
                        .connect().sync().channel();


                QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                        .streamHandler(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) {
                                // As we did not allow any remote initiated streams we will never see this method called.
                                // That said just let us keep it here to demonstrate that this handle would be called
                                // for each remote initiated stream.

                                LogUtils.error(TAG, "quicChannel invoked");
                                ctx.close();
                            }
                        })
                        .remoteAddress(new InetSocketAddress(NetUtil.LOCALHOST4, 9999))
                        .connect()
                        .get();

                QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                        new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                ByteBuf byteBuf = (ByteBuf) msg;
                                LogUtils.error(TAG, byteBuf.toString(CharsetUtil.US_ASCII));
                                byteBuf.release();
                            }

                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                                    // Close the connection once the remote peer did send the FIN for this stream.
                                    ((QuicChannel) ctx.channel().parent()).close(true, 0,
                                            ctx.alloc().directBuffer(16)
                                                    .writeBytes(new byte[]{'k', 't', 'h', 'x', 'b', 'y', 'e'}));
                                }
                            }
                        }).sync().getNow();
                // Write the data and send the FIN. After this its not possible anymore to write any more data.
                streamChannel.writeAndFlush(Unpooled.copiedBuffer("GET /\r\n", CharsetUtil.US_ASCII))
                        .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);

                // Wait for the stream channel and quic channel to be closed (this will happen after we received the FIN).
                // After this is done we will close the underlying datagram channel.
                streamChannel.closeFuture().sync();
                quicChannel.closeFuture().sync();
                channel.close().sync();
            } finally {
                group.shutdownGracefully();
            }
        }
    }
}


