package threads.thor.bt.processor.magnet;

import threads.thor.bt.data.Storage;
import threads.thor.bt.magnet.MagnetUri;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.processor.torrent.TorrentContext;
import threads.thor.bt.torrent.messaging.BitfieldCollectingConsumer;
import threads.thor.bt.torrent.selector.PieceSelector;

public class MagnetContext extends TorrentContext {

    private final MagnetUri magnetUri;
    private volatile BitfieldCollectingConsumer bitfieldConsumer;

    public MagnetContext(MagnetUri magnetUri,
                         PieceSelector pieceSelector,
                         Storage storage) {
        super(pieceSelector, storage, null);
        this.magnetUri = magnetUri;
    }


    public MagnetUri getMagnetUri() {
        return magnetUri;
    }

    @Override
    public TorrentId getTorrentId() {
        return magnetUri.getTorrentId();
    }

    @Override
    public void setTorrentId(TorrentId torrentId) {
        throw new UnsupportedOperationException();
    }

    public BitfieldCollectingConsumer getBitfieldConsumer() {
        return bitfieldConsumer;
    }

    public void setBitfieldConsumer(BitfieldCollectingConsumer bitfieldConsumer) {
        this.bitfieldConsumer = bitfieldConsumer;
    }
}
