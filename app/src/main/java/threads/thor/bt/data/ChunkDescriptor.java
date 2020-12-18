package threads.thor.bt.data;

public class ChunkDescriptor implements BlockSet {

    private final DataRange data;
    private final BlockSet blockSet;

    /**
     * Hash of this chunk's contents; used to verify integrity of chunk's data
     */
    private final byte[] checksum;

    /**
     * @param data     Subrange of the threads.torrent data, that this chunk represents
     * @param blockSet Data represented as a set of blocks
     * @param checksum Chunk's hash
     */
    public ChunkDescriptor(DataRange data,
                           BlockSet blockSet,
                           byte[] checksum) {
        this.data = data;
        this.blockSet = blockSet;
        this.checksum = checksum;
    }


    public byte[] getChecksum() {
        return checksum;
    }


    public DataRange getData() {
        return data;
    }

    @Override
    public int blockCount() {
        return blockSet.blockCount();
    }

    @Override
    public long length() {
        return blockSet.length();
    }

    @Override
    public long blockSize() {
        return blockSet.blockSize();
    }

    @Override
    public long lastBlockSize() {
        return blockSet.lastBlockSize();
    }

    @Override
    public boolean isPresent(int blockIndex) {
        return blockSet.isPresent(blockIndex);
    }

    @Override
    public boolean isComplete() {
        return blockSet.isComplete();
    }

    @Override
    public boolean isEmpty() {
        return blockSet.isEmpty();
    }

    @Override
    public void clear() {
        blockSet.clear();
    }
}
