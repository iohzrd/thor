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
