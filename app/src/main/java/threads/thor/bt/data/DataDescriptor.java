package threads.thor.bt.data;

import androidx.annotation.NonNull;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import threads.LogUtils;
import threads.thor.Settings;
import threads.thor.bt.data.range.BlockRange;
import threads.thor.bt.data.range.Ranges;
import threads.thor.bt.data.range.SynchronizedBlockSet;
import threads.thor.bt.data.range.SynchronizedDataRange;
import threads.thor.bt.data.range.SynchronizedRange;
import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentFile;


public class DataDescriptor implements Closeable {

    private static final String TAG = DataDescriptor.class.getSimpleName();
    private final Storage storage;

    private final Torrent torrent;

    private final ChunkVerifier verifier;
    private List<ChunkDescriptor> chunkDescriptors;
    private Bitfield bitfield;
    private Map<Integer, List<TorrentFile>> filesForPieces;
    private Set<StorageUnit> storageUnits;

    DataDescriptor(Storage storage,
                   Torrent torrent,
                   ChunkVerifier verifier) {
        this.storage = storage;
        this.torrent = torrent;
        this.verifier = verifier;

        init();

    }

    private void init() {
        long transferBlockSize = Settings.transferBlockSize;
        List<TorrentFile> files = torrent.getFiles();

        long totalSize = torrent.getSize();
        long chunkSize = torrent.getChunkSize();

        if (transferBlockSize > chunkSize) {
            transferBlockSize = chunkSize;
        }

        @SuppressWarnings("IntegerDivisionInFloatingPointContext")
        int chunksTotal = (int) Math.ceil(totalSize / chunkSize);
        Map<Integer, List<TorrentFile>> filesForPieces = new HashMap<>((int) (chunksTotal / 0.75d) + 1);
        List<ChunkDescriptor> chunks = new ArrayList<>(chunksTotal + 1);

        Iterator<byte[]> chunkHashes = torrent.getIterableChunkHashes().iterator();

        Map<StorageUnit, TorrentFile> storageUnitsToFilesMap = new LinkedHashMap<>((int) (files.size() / 0.75d) + 1);
        files.forEach(f -> storageUnitsToFilesMap.put(storage.getUnit(torrent, f), f));

        // filter out empty files (and create them at once)
        List<StorageUnit> nonEmptyStorageUnits = new ArrayList<>();
        for (StorageUnit unit : storageUnitsToFilesMap.keySet()) {
            if (unit.capacity() > 0) {
                nonEmptyStorageUnits.add(unit);
            }
        }

        if (nonEmptyStorageUnits.size() > 0) {
            long limitInLastUnit = nonEmptyStorageUnits.get(nonEmptyStorageUnits.size() - 1).capacity();
            DataRange data = new ReadWriteDataRange(nonEmptyStorageUnits, 0, limitInLastUnit);

            long off, lim;
            long remaining = totalSize;
            while (remaining > 0) {
                off = chunks.size() * chunkSize;
                lim = Math.min(chunkSize, remaining);

                DataRange subrange = data.getSubrange(off, lim);

                if (!chunkHashes.hasNext()) {
                    throw new RuntimeException("Wrong number of chunk hashes in the torrent: too few");
                }

                List<TorrentFile> chunkFiles = new ArrayList<>();
                subrange.visitUnits((unit, off1, lim1) -> chunkFiles.add(storageUnitsToFilesMap.get(unit)));
                filesForPieces.put(chunks.size(), chunkFiles);

                chunks.add(buildChunkDescriptor(subrange, transferBlockSize, chunkHashes.next()));

                remaining -= chunkSize;
            }
        }

        if (chunkHashes.hasNext()) {
            throw new RuntimeException("Wrong number of chunk hashes in the threads.torrent: too many");
        }

        this.bitfield = buildBitfield(chunks);
        this.chunkDescriptors = chunks;
        this.storageUnits = storageUnitsToFilesMap.keySet();
        this.filesForPieces = filesForPieces;

    }

    private ChunkDescriptor buildChunkDescriptor(DataRange data, long blockSize, byte[] checksum) {
        BlockRange<DataRange> blockData = Ranges.blockRange(data, blockSize);
        SynchronizedRange<BlockRange<DataRange>> synchronizedRange = new SynchronizedRange<>(blockData);
        SynchronizedDataRange<BlockRange<DataRange>> synchronizedData =
                new SynchronizedDataRange<>(synchronizedRange, BlockRange::getDelegate);
        SynchronizedBlockSet synchronizedBlockSet = new SynchronizedBlockSet(blockData.getBlockSet(), synchronizedRange);

        return new ChunkDescriptor(synchronizedData, synchronizedBlockSet, checksum);
    }

    private Bitfield buildBitfield(List<ChunkDescriptor> chunks) {
        Bitfield bitfield = new Bitfield(chunks.size());
        verifier.verify(torrent.getTorrentId(), chunks, bitfield);
        return bitfield;
    }


    public List<ChunkDescriptor> getChunkDescriptors() {
        return chunkDescriptors;
    }


    public Bitfield getBitfield() {
        return bitfield;
    }


    public List<TorrentFile> getFilesForPiece(int pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= bitfield.getPiecesTotal()) {
            throw new IllegalArgumentException("Invalid piece index: " + pieceIndex +
                    ", expected 0.." + bitfield.getPiecesTotal());
        }
        return filesForPieces.get(pieceIndex);
    }

    @Override
    public void close() {
        storageUnits.forEach(unit -> {
            try {
                unit.close();
            } catch (Exception e) {
                LogUtils.error(TAG, "Failed to close storage unit: " + unit);
            }
        });
    }

    @NonNull
    @Override
    public String toString() {
        return this.getClass().getName() + " <" + torrent.getName() + ">";
    }
}
