package threads.thor.bt.net;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import threads.LogUtils;
import threads.thor.bt.Config;
import threads.thor.bt.CountingThreadFactory;

class IncomingConnectionListener {
    private static final String TAG = IncomingConnectionListener.class.getSimpleName();
    private final Set<PeerConnectionAcceptor> connectionAcceptors;
    private final ExecutorService connectionExecutor;
    private final IPeerConnectionPool connectionPool;
    private final Config config;

    private final ExecutorService executor;
    private volatile boolean shutdown;

    IncomingConnectionListener(Set<PeerConnectionAcceptor> connectionAcceptors,
                               ExecutorService connectionExecutor,
                               IPeerConnectionPool connectionPool,
                               Config config) {
        this.connectionAcceptors = connectionAcceptors;
        this.connectionExecutor = connectionExecutor;
        this.connectionPool = connectionPool;
        this.config = config;

        this.executor = Executors.newFixedThreadPool(
                connectionAcceptors.size(),
                CountingThreadFactory.factory("bt.net.pool.incoming-acceptor"));
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
        return connectionPool.size() < config.getMaxPeerConnections();
    }

    public void shutdown() {
        shutdown = true;
        executor.shutdownNow();
    }
}
