package threads.thor.bt.net;


import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import threads.LogUtils;


public class SocketChannelConnectionAcceptor {

    private final static String TAG = SocketChannelConnectionAcceptor.class.getSimpleName();
    private final Selector selector;
    private final PeerConnectionFactory connectionFactory;
    private final InetSocketAddress localAddress;

    private ServerSocketChannel serverChannel;

    public SocketChannelConnectionAcceptor(
            Selector selector,
            PeerConnectionFactory connectionFactory,
            InetSocketAddress localAddress) {

        this.selector = selector;
        this.connectionFactory = connectionFactory;
        this.localAddress = localAddress;
    }


    public ConnectionRoutine accept() {
        ServerSocketChannel serverChannel;
        SocketAddress localAddress;
        try {
            serverChannel = getServerChannel();
            localAddress = serverChannel.getLocalAddress();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create incoming connection acceptor " +
                    "-- unexpected I/O exception happened when creating an incoming channel", e);
        }

        try {
            SocketChannel channel;
            SocketAddress remoteAddress = null;

            do {
                channel = serverChannel.accept();
                if (channel != null) {
                    try {
                        remoteAddress = channel.getRemoteAddress();
                    } catch (IOException e) {
                        LogUtils.error(TAG, "Failed to establish incoming connection", e);
                    }
                }
            } while (channel == null || remoteAddress == null);

            return getConnectionRoutine(channel, remoteAddress);

        } catch (ClosedChannelException e) {
            throw new RuntimeException("Incoming channel @ " +
                    localAddress + " has been closed, will stop accepting incoming connections...");
        } catch (IOException e) {
            throw new RuntimeException("Unexpected I/O error when listening to the incoming channel @ " +
                    localAddress + ", will stop accepting incoming connections...", e);
        }
    }

    /**
     * @return Local socket channel, used for accepting the incoming connections
     */
    private ServerSocketChannel getServerChannel() throws IOException {
        if (serverChannel == null) {
            ServerSocketChannel _serverChannel = selector.provider().openServerSocketChannel();
            _serverChannel.bind(localAddress);
            _serverChannel.configureBlocking(true);
            serverChannel = _serverChannel;

        }
        return serverChannel;
    }

    private ConnectionRoutine getConnectionRoutine(SocketChannel incomingChannel, SocketAddress remoteAddress) {
        return new ConnectionRoutine() {
            @Override
            public SocketAddress getRemoteAddress() {
                return remoteAddress;
            }

            @Override
            public ConnectionResult establish() {
                return createConnection(incomingChannel, remoteAddress);
            }

            @Override
            public void cancel() {
                try {
                    incomingChannel.close();
                } catch (IOException e) {
                    LogUtils.error(TAG, "Failed to close incoming channel: " + remoteAddress, e);
                }
            }
        };
    }

    private ConnectionResult createConnection(SocketChannel incomingChannel, SocketAddress remoteAddress) {
        try {
            InetAddress address = ((InetSocketAddress) remoteAddress).getAddress();
            Peer peer = InetPeer.builder(address).build();
            return connectionFactory.createIncomingConnection(peer, incomingChannel);
        } catch (Exception e) {
            LogUtils.error(TAG, "Failed to establish incoming connection from peer: " + remoteAddress, e);
            try {
                incomingChannel.close();
            } catch (IOException e1) {
                LogUtils.error(TAG, e1);
            }
            return ConnectionResult.failure("Unexpected error", e);
        }
    }
}
