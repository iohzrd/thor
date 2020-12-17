package threads.thor.bt.torrent;

public final class TorrentSessionState {


    private final TorrentDescriptor descriptor;


    public TorrentSessionState(TorrentDescriptor descriptor) {
        this.descriptor = descriptor;

    }

    public int getPiecesTotal() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getPiecesTotal();
        } else {
            return 1;
        }
    }


    public int getPiecesComplete() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getPiecesComplete();
        } else {
            return 0;
        }
    }


    public int getPiecesRemaining() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getPiecesRemaining();
        } else {
            return 1;
        }
    }

}
