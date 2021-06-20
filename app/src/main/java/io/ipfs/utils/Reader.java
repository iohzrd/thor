package io.ipfs.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.core.Interface;
import io.ipfs.dag.BlockService;
import io.ipfs.dag.DagReader;
import io.ipfs.dag.DagService;
import io.ipfs.format.BlockStore;

public class Reader {

    private final DagReader dagReader;
    private final Closeable closeable;


    private Reader(@NonNull Closeable closeable, @NonNull DagReader dagReader) {
        this.closeable = closeable;
        this.dagReader = dagReader;
    }

    public static Reader getReader(@NonNull Closeable closeable, @NonNull BlockStore blockstore,
                                   @NonNull Interface exchange, @NonNull Cid cid) throws ClosedException {
        BlockService blockservice = BlockService.createBlockService(blockstore, exchange);
        DagService dags = DagService.createDagService(blockservice);
        io.ipfs.format.Node top = Resolver.resolveNode(closeable, dags, cid);
        Objects.requireNonNull(top);
        DagReader dagReader = DagReader.create(top, dags);

        return new Reader(closeable, dagReader);
    }

    public int readNextData(long offset, int size, byte[] data) throws ClosedException {
        seek(offset);
        byte[] bytes = loadNextData();
        if (bytes != null) {
            int min = Math.min(bytes.length, size);
            System.arraycopy(bytes, 0, data, 0, min);
            if (min < size) {
                int remain = size - min;
                bytes = loadNextData();
                if (bytes != null) {
                    System.arraycopy(bytes, 0, data, min, remain);
                    return size;
                } else {
                    return min;
                }
            }
            return min;
        }

        return 0;
    }

    public void seek(long position) throws ClosedException {
        dagReader.Seek(closeable, position);
    }

    @Nullable
    public byte[] loadNextData() throws ClosedException {
        try {
            return dagReader.loadNextData(closeable);
        } finally {
            dagReader.preloadData(closeable);
        }
    }

    public long getSize() {
        return this.dagReader.getSize();
    }
}
