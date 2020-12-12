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

package threads.thor.bt.torrent.data;

import androidx.annotation.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import threads.LogUtils;
import threads.thor.bt.data.ChunkDescriptor;
import threads.thor.bt.data.ChunkVerifier;
import threads.thor.bt.data.DataDescriptor;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.Peer;
import threads.thor.bt.net.buffer.BufferedData;
import threads.thor.bt.service.RuntimeLifecycleBinder;
import threads.thor.bt.torrent.TorrentRegistry;

public class DefaultDataWorker implements DataWorker {

    private static final String TAG = DefaultDataWorker.class.getSimpleName();
    private static final Exception QUEUE_FULL_EXCEPTION = new IllegalStateException("Queue is overloaded");

    private final TorrentRegistry torrentRegistry;
    private final ChunkVerifier verifier;
    private final BlockCache blockCache;

    private final ExecutorService executor;
    private final int maxPendingTasks;
    private final AtomicInteger pendingTasksCount;

    public DefaultDataWorker(RuntimeLifecycleBinder lifecycleBinder,
                             TorrentRegistry torrentRegistry,
                             ChunkVerifier verifier,
                             BlockCache blockCache,
                             int maxQueueLength) {

        this.torrentRegistry = torrentRegistry;
        this.verifier = verifier;
        this.blockCache = blockCache;

        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {

            private final AtomicInteger i = new AtomicInteger();

            @Override
            public Thread newThread(@NonNull Runnable r) {
                return new Thread(r, "bt.threads.torrent.data.worker-" + i.incrementAndGet());
            }
        });
        this.maxPendingTasks = maxQueueLength;
        this.pendingTasksCount = new AtomicInteger();

        lifecycleBinder.onShutdown("Shutdown data worker", this.executor::shutdownNow);
    }

    @Override
    public CompletableFuture<BlockRead> addBlockRequest(TorrentId torrentId, Peer peer, int pieceIndex, int offset, int length) {
        DataDescriptor data = getDataDescriptor(torrentId);
        if (!data.getBitfield().isVerified(pieceIndex)) {

            return CompletableFuture.completedFuture(BlockRead.rejected(peer, pieceIndex, offset, length));
        } else if (!tryIncrementTaskCount()) {

            return CompletableFuture.completedFuture(BlockRead.exceptional(peer,
                    QUEUE_FULL_EXCEPTION, pieceIndex, offset, length));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                BlockReader blockReader = blockCache.get(torrentId, pieceIndex, offset, length);
                return BlockRead.ready(peer, pieceIndex, offset, length, blockReader);
            } catch (Throwable e) {
                LogUtils.error(TAG, "Failed to perform request to read block:" +
                        " piece index {" + pieceIndex + "}, offset {" + offset + "}, length {" + length + "}, peer {" + peer + "}", e);
                return BlockRead.exceptional(peer, e, pieceIndex, offset, length);
            } finally {
                pendingTasksCount.decrementAndGet();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<BlockWrite> addBlock(TorrentId torrentId, Peer peer, int pieceIndex, int offset, BufferedData buffer) {
        if (!tryIncrementTaskCount()) {

            buffer.dispose();
            return CompletableFuture.completedFuture(BlockWrite.exceptional(peer,
                    QUEUE_FULL_EXCEPTION, pieceIndex, offset, buffer.length()));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                DataDescriptor data = getDataDescriptor(torrentId);
                ChunkDescriptor chunk = data.getChunkDescriptors().get(pieceIndex);

                if (chunk.isComplete()) {

                    return BlockWrite.rejected(peer, pieceIndex, offset, buffer.length());
                }

                chunk.getData().getSubrange(offset).putBytes(buffer.buffer());


                CompletableFuture<Boolean> verificationFuture = null;
                if (chunk.isComplete()) {
                    verificationFuture = CompletableFuture.supplyAsync(() -> {
                        boolean verified = verifier.verify(chunk);
                        if (verified) {
                            data.getBitfield().markVerified(pieceIndex);
                        } else {
                            // reset data
                            chunk.clear();
                        }
                        return verified;
                    }, executor);
                }

                return BlockWrite.complete(peer, pieceIndex, offset, buffer.length(), verificationFuture);
            } catch (Throwable e) {
                return BlockWrite.exceptional(peer, e, pieceIndex, offset, buffer.length());
            } finally {
                pendingTasksCount.decrementAndGet();
                buffer.dispose();
            }
        }, executor);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean tryIncrementTaskCount() {
        int newCount = pendingTasksCount.updateAndGet(oldCount -> {
            if (oldCount == maxPendingTasks) {
                return oldCount;
            } else {
                return oldCount + 1;
            }
        });
        return newCount < maxPendingTasks;
    }

    private DataDescriptor getDataDescriptor(TorrentId torrentId) {
        return torrentRegistry.getDescriptor(torrentId).get().getDataDescriptor();
    }
}
