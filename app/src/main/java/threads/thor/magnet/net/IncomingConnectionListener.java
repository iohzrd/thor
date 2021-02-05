package threads.thor.magnet.net;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import threads.LogUtils;
import threads.thor.Settings;

class IncomingConnectionListener {
    private static final String TAG = IncomingConnectionListener.class.getSimpleName();
    private final Set<SocketChannelConnectionAcceptor> connectionAcceptors;
    private final ExecutorService connectionExecutor;
    private final PeerConnectionPool connectionPool;


    private final ExecutorService executor;
    private volatile boolean shutdown;

    IncomingConnectionListener(Set<SocketChannelConnectionAcceptor> connectionAcceptors,
                               ExecutorService connectionExecutor,
                               PeerConnectionPool connectionPool) {
        this.connectionAcceptors = connectionAcceptors;
        this.connectionExecutor = connectionExecutor;
        this.connectionPool = connectionPool;

        this.executor = Executors.newFixedThreadPool(connectionAcceptors.size());
    }

    public void startup() {
        connectionAcceptors.forEach(acceptor ->
                executor.submit(() -> {
                    ConnectionRoutine connectionRoutine;

                    while (!shutdown) {
                        try {
                            connectionRoutine = acceptor.accept();
                        } catch (Exception e) {
                            LogUtils.error(TAG, "Unexpected error", e);
                            return;
                        }

                        if (mightAddConnection()) {
                            establishConnection(connectionRoutine);
                        } else {
                            connectionRoutine.cancel();
                        }
                    }
                }));
    }

    private void establishConnection(ConnectionRoutine connectionRoutine) {
        connectionExecutor.submit(() -> {
            boolean added = false;
            if (!shutdown) {
                ConnectionResult connectionResult = connectionRoutine.establish();
                if (connectionResult.isSuccess()) {
                    if (!shutdown && mightAddConnection()) {
                        PeerConnection established = connectionResult.getConnection();
                        PeerConnection existing = connectionPool.addConnectionIfAbsent(established);
                        added = (established == existing);
                    }
                }
            }
            if (!added) {
                connectionRoutine.cancel();
            }
        });
    }

    private boolean mightAddConnection() {
        return connectionPool.size() < Settings.maxPeerConnections;
    }

    public void shutdown() {
        shutdown = true;
        executor.shutdownNow();
    }
}
