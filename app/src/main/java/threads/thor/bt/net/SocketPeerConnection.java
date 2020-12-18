package threads.thor.bt.net;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import threads.LogUtils;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.pipeline.SocketChannelHandler;
import threads.thor.bt.protocol.Message;

public class SocketPeerConnection implements PeerConnection {
    private static final String TAG = SocketPeerConnection.class.getSimpleName();
    private static final long WAIT_BETWEEN_READS = 100L;

    private final AtomicReference<TorrentId> torrentId;
    private final Peer remotePeer;
    private final int remotePort;

    private final SocketChannelHandler handler;

    private final AtomicLong lastActive;

    private final ReentrantLock readLock;
    private final Condition condition;

    SocketPeerConnection(Peer remotePeer, int remotePort, SocketChannelHandler handler) {
        this.torrentId = new AtomicReference<>();
        this.remotePeer = remotePeer;
        this.remotePort = remotePort;
        this.handler = handler;
        this.lastActive = new AtomicLong();
        this.readLock = new ReentrantLock(true);
        this.condition = this.readLock.newCondition();
    }

    /**
     * @since 1.0
     */
    @Override
    public TorrentId setTorrentId(TorrentId torrentId) {
        return this.torrentId.getAndSet(torrentId);
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId.get();
    }

    @Override
    public synchronized Message readMessageNow() {
        Message message = handler.receive();
        if (message != null) {
            updateLastActive();

        }
        return message;
    }

    @Override
    public synchronized Message readMessage(long timeout) {
        Message message = readMessageNow();
        if (message == null) {


            long remaining = timeout;

            // ... wait for the incoming message
            while (!handler.isClosed()) {
                try {
                    readLock.lock();
                    try {
                        condition.await(Math.min(timeout, WAIT_BETWEEN_READS), TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Unexpectedly interrupted", e);
                    }
                    remaining -= WAIT_BETWEEN_READS;
                    message = readMessageNow();
                    if (message != null) {

                        return message;
                    } else if (remaining <= 0) {

                        return null;
                    }
                } finally {
                    readLock.unlock();
                }
            }
        }
        return message;
    }

    @Override
    public synchronized void postMessage(Message message) {
        updateLastActive();

        handler.send(message);
    }

    private void updateLastActive() {
        lastActive.set(System.currentTimeMillis());
    }

    @Override
    public Peer getRemotePeer() {
        return remotePeer;
    }

    @Override
    public int getRemotePort() {
        return remotePort;
    }

    @Override
    public void closeQuietly() {
        try {
            close();
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    @Override
    public void close() {
        if (!isClosed()) {

            handler.close();
        }
    }

    @Override
    public boolean isClosed() {
        return handler.isClosed();
    }

    @Override
    public long getLastActive() {
        return lastActive.get();
    }

}
