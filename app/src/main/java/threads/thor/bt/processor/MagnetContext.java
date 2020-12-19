package threads.thor.bt.processor;

import androidx.annotation.NonNull;

import threads.thor.bt.data.Storage;
import threads.thor.bt.magnet.MagnetUri;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.torrent.BitfieldCollectingConsumer;
import threads.thor.bt.torrent.PieceSelector;

public class MagnetContext extends TorrentContext {

    private final MagnetUri magnetUri;
    private volatile BitfieldCollectingConsumer bitfieldConsumer;

    public MagnetContext(MagnetUri magnetUri, PieceSelector pieceSelector, Storage storage) {
        super(pieceSelector, storage);
        this.magnetUri = magnetUri;
    }

    public MagnetUri getMagnetUri() {
        return magnetUri;
    }

    @NonNull
    public TorrentId getTorrentId() {
        return magnetUri.getTorrentId();
    }


    public BitfieldCollectingConsumer getBitfieldConsumer() {
        return bitfieldConsumer;
    }

    public void setBitfieldConsumer(BitfieldCollectingConsumer bitfieldConsumer) {
        this.bitfieldConsumer = bitfieldConsumer;
    }
}