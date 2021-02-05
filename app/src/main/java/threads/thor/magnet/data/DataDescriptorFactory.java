package threads.thor.magnet.data;

import threads.thor.magnet.metainfo.Torrent;

public final class DataDescriptorFactory {

    private final ChunkVerifier verifier;


    public DataDescriptorFactory(ChunkVerifier verifier) {
        this.verifier = verifier;
    }

    public DataDescriptor createDescriptor(Torrent torrent, Storage storage) {
        return new DataDescriptor(storage, torrent, verifier);
    }
}
