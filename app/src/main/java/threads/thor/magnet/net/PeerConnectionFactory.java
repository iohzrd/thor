package threads.thor.magnet.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.Objects;

import threads.LogUtils;
import threads.thor.Settings;
import threads.thor.magnet.event.EventSource;
import threads.thor.magnet.metainfo.TorrentId;
import threads.thor.magnet.net.buffer.BorrowedBuffer;
import threads.thor.magnet.net.buffer.IBufferManager;
import threads.thor.magnet.net.crypto.CipherBufferMutator;
import threads.thor.magnet.net.crypto.MSEHandshakeProcessor;
import threads.thor.magnet.net.pipeline.ChannelPipeline;
import threads.thor.magnet.net.pipeline.ChannelPipelineBuilder;
import threads.thor.magnet.net.pipeline.ChannelPipelineFactory;
import threads.thor.magnet.net.pipeline.SocketChannelHandler;
import threads.thor.magnet.protocol.Message;
import threads.thor.magnet.protocol.crypto.MSECipher;
import threads.thor.magnet.protocol.handler.MessageHandler;
import threads.thor.magnet.torrent.TorrentRegistry;

public class PeerConnectionFactory {
    private static final String TAG = PeerConnectionFactory.class.getSimpleName();
    private static final Duration socketTimeout = Duration.ofSeconds(30);

    private final MessageHandler<Message> protocol;

    private final Selector selector;
    private final ConnectionHandlerFactory connectionHandlerFactory;
    private final ChannelPipelineFactory channelPipelineFactory;
    private final IBufferManager bufferManager;
    private final MSEHandshakeProcessor cryptoHandshakeProcessor;
    private final DataReceiver dataReceiver;
    private final EventSource eventSource;

    private final InetSocketAddress localOutgoingSocketAddress;

    public PeerConnectionFactory(Selector selector,
                                 ConnectionHandlerFactory connectionHandlerFactory,
                                 ChannelPipelineFactory channelPipelineFactory,
                                 MessageHandler<Message> protocol,
                                 TorrentRegistry torrentRegistry,
                                 IBufferManager bufferManager,
                                 DataReceiver dataReceiver,
                                 EventSource eventSource) {

        this.protocol = protocol;
        this.selector = selector;
        this.connectionHandlerFactory = connectionHandlerFactory;
        this.channelPipelineFactory = channelPipelineFactory;
        this.bufferManager = bufferManager;
        this.cryptoHandshakeProcessor = new MSEHandshakeProcessor(torrentRegistry, protocol);
        this.dataReceiver = dataReceiver;
        this.eventSource = eventSource;
        this.localOutgoingSocketAddress = new InetSocketAddress(Settings.acceptorAddress, 0);
    }


    public ConnectionResult createOutgoingConnection(Peer peer, TorrentId torrentId) {
        Objects.requireNonNull(peer);
        Objects.requireNonNull(torrentId);

        InetAddress inetAddress = peer.getInetAddress();
        int port = peer.getPort();

        SocketChannel channel;
        try {
            channel = getChannel(inetAddress, port);
        } catch (IOException e) {
            return ConnectionResult.failure();
        }

        return createConnection(peer, torrentId, channel, false);
    }

    private SocketChannel getChannel(InetAddress inetAddress, int port) throws IOException {
        InetSocketAddress remoteAddress = new InetSocketAddress(inetAddress, port);
        SocketChannel outgoingChannel = selector.provider().openSocketChannel();
        outgoingChannel.socket().bind(localOutgoingSocketAddress);
        outgoingChannel.socket().setSoTimeout((int) socketTimeout.toMillis());
        outgoingChannel.socket().setSoLinger(false, 0);
        outgoingChannel.connect(remoteAddress);
        return outgoingChannel;
    }


    public ConnectionResult createIncomingConnection(Peer peer, SocketChannel channel) {
        return createConnection(peer, null, channel, true);
    }

    private ConnectionResult createConnection(Peer peer, TorrentId torrentId, SocketChannel channel, boolean incoming) {
        BorrowedBuffer<ByteBuffer> in = bufferManager.borrowByteBuffer();
        BorrowedBuffer<ByteBuffer> out = bufferManager.borrowByteBuffer();
        try {
            return _createConnection(peer, torrentId, channel, incoming, in, out);
        } catch (Exception e) {

            closeQuietly(channel);
            releaseBuffer(in);
            releaseBuffer(out);
            return ConnectionResult.failure();
        }
    }

    private ConnectionResult _createConnection(
            Peer peer,
            TorrentId torrentId,
            SocketChannel channel,
            boolean incoming,
            BorrowedBuffer<ByteBuffer> in,
            BorrowedBuffer<ByteBuffer> out) throws IOException {

        // sanity check
        if (!incoming && torrentId == null) {
            throw new IllegalStateException("Requested outgoing connection without threads.torrent ID. Peer: " + peer);
        }

        channel.configureBlocking(false);

        ByteBuffer inBuffer = in.lockAndGet();
        ByteBuffer outBuffer = out.lockAndGet();
        MSECipher cipher;
        try {
            if (incoming) {
                cipher = cryptoHandshakeProcessor.negotiateIncoming(peer, channel, inBuffer, outBuffer);
            } else {
                cipher = cryptoHandshakeProcessor.negotiateOutgoing(channel, torrentId, inBuffer, outBuffer);
            }
        } finally {
            in.unlock();
            out.unlock();
        }

        ChannelPipeline pipeline = createPipeline(peer, channel, in, out, cipher);
        SocketChannelHandler channelHandler = new SocketChannelHandler(
                channel, in, out, pipeline::bindHandler, dataReceiver);
        channelHandler.register();

        int remotePort = ((InetSocketAddress) channel.getRemoteAddress()).getPort();
        PeerConnection connection = new SocketPeerConnection(peer, remotePort, channelHandler);
        ConnectionHandler connectionHandler;
        if (incoming) {
            connectionHandler = connectionHandlerFactory.getIncomingHandler();
        } else {
            connectionHandler = connectionHandlerFactory.getOutgoingHandler(torrentId);
        }
        boolean inited = initConnection(connection, connectionHandler);
        if (inited) {
            subscribeHandler(connection.getTorrentId(), channelHandler);
            return ConnectionResult.success(connection);
        } else {
            connection.closeQuietly();
            return ConnectionResult.failure();
        }
    }

    private void subscribeHandler(TorrentId torrentId, SocketChannelHandler channelHandler) {
        eventSource.onTorrentStarted(event -> {
            if (event.getTorrentId().equals(torrentId)) {
                channelHandler.activate();
            }
        });
        eventSource.onTorrentStopped(event -> {
            if (event.getTorrentId().equals(torrentId)) {
                channelHandler.deactivate();
            }
        });
    }

    private ChannelPipeline createPipeline(
            Peer peer,
            ByteChannel channel,
            BorrowedBuffer<ByteBuffer> in,
            BorrowedBuffer<ByteBuffer> out,
            MSECipher cipher) {

        ChannelPipelineBuilder builder = channelPipelineFactory.buildPipeline(peer);
        builder.channel(channel);
        builder.protocol(protocol);
        builder.inboundBuffer(in);
        builder.outboundBuffer(out);

        if (cipher != null) {
            builder.decoders(new CipherBufferMutator(cipher.getDecryptionCipher()));
            builder.encoders(new CipherBufferMutator(cipher.getEncryptionCipher()));
        }

        return builder.build();
    }

    private boolean initConnection(PeerConnection newConnection, ConnectionHandler connectionHandler) {
        return connectionHandler.handleConnection(newConnection);
    }

    private void closeQuietly(SocketChannel channel) {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void releaseBuffer(BorrowedBuffer<ByteBuffer> buffer) {
        try {
            buffer.release();
        } catch (Exception e) {
            LogUtils.error(TAG, "Failed to release buffer", e);
        }
    }
}
