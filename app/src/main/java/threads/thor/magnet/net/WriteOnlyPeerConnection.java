package threads.thor.magnet.net;

import java.io.IOException;

import threads.thor.magnet.metainfo.TorrentId;
import threads.thor.magnet.protocol.Message;

class WriteOnlyPeerConnection implements PeerConnection {

    private final PeerConnection delegate;

    WriteOnlyPeerConnection(PeerConnection delegate) {
        this.delegate = delegate;
    }

    @Override
    public Peer getRemotePeer() {
        return delegate.getRemotePeer();
    }

    @Override
    public int getRemotePort() {
        return delegate.getRemotePort();
    }

    @Override
    public TorrentId setTorrentId(TorrentId torrentId) {
        return delegate.setTorrentId(torrentId);
    }

    @Override
    public TorrentId getTorrentId() {
        return delegate.getTorrentId();
    }

    @Override
    public Message readMessageNow() {
        throw new UnsupportedOperationException("Connection is write-only");
    }

    @Override
    public Message readMessage(long timeout) {
        throw new UnsupportedOperationException("Connection is write-only");
    }

    @Override
    public void postMessage(Message message) throws IOException {
        delegate.postMessage(message);
    }

    @Override
    public long getLastActive() {
        return delegate.getLastActive();
    }

    @Override
    public void closeQuietly() {
        delegate.closeQuietly();
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
