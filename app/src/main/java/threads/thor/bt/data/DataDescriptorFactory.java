package threads.thor.bt.data;

import threads.thor.bt.metainfo.Torrent;

public final class DataDescriptorFactory {

    private final DataReaderFactory dataReaderFactory;
    private final ChunkVerifier verifier;
    private final int transferBlockSize;

    public DataDescriptorFactory(
            DataReaderFactory dataReaderFactory,
            ChunkVerifier verifier,
            int transferBlockSize) {

        this.dataReaderFactory = dataReaderFactory;
        this.verifier = verifier;
        this.transferBlockSize = transferBlockSize;
    }

    public DataDescriptor createDescriptor(Torrent torrent, Storage storage) {
        return new DefaultDataDescriptor(storage, torrent, verifier, dataReaderFactory, transferBlockSize);
    }
}
