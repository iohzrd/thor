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

package threads.thor.bt.peer;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import threads.LogUtils;
import threads.thor.bt.net.Peer;

/**
 * @since 1.1
 */
public abstract class ScheduledPeerSource implements PeerSource {

    private final static String TAG = ScheduledPeerSource.class.getSimpleName();
    private final ExecutorService executor;
    private final ReentrantLock lock;
    private final AtomicReference<Future<?>> futureOptional;
    private final Queue<Peer> peers;

    protected ScheduledPeerSource(ExecutorService executor) {
        this.executor = executor;
        this.lock = new ReentrantLock();
        this.futureOptional = new AtomicReference<>();
        this.peers = new LinkedBlockingQueue<>();
    }

    @Override
    public Collection<Peer> getPeers() {
        return peers;
    }

    @Override
    public boolean update() {
        if (peers.isEmpty()) {
            schedulePeerCollection();
        }
        return !peers.isEmpty();
    }

    private void schedulePeerCollection() {
        if (lock.tryLock()) {
            try {
                if (futureOptional.get() != null) {
                    Future<?> future = futureOptional.get();
                    if (future.isDone()) {
                        try {
                            future.get();
                        } catch (InterruptedException | ExecutionException e) {
                            LogUtils.error(TAG,
                                    "Peer collection finished with exception in peer source: " + toString(), e);
                        }
                        futureOptional.set(null);
                    }
                }

                if (futureOptional.get() == null) {
                    futureOptional.set(executor.submit(() -> collectPeers(peers::add)));
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * @since 1.1
     */
    protected abstract void collectPeers(Consumer<Peer> peerConsumer);
}
