package threads.thor.bt.data;

import threads.thor.bt.metainfo.Torrent;

public final class DataDescriptorFactory {

    private final ChunkVerifier verifier;
    private final int transferBlockSize;

    public DataDescriptorFactory(
            ChunkVerifier verifier,
            int transferBlockSize) {

        this.verifier = verifier;
        this.transferBlockSize = transferBlockSize;
    }

    public DataDescriptor createDescriptor(Torrent torrent, Storage storage) {
        return new DataDescriptor(storage, torrent, verifier, transferBlockSize);
    }
}
