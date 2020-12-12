/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package threads.thor.bt.net;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import threads.LogUtils;
import threads.thor.bt.CountingThreadFactory;
import threads.thor.bt.runtime.Config;

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
