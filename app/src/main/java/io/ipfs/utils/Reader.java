package io.ipfs.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import io.Closeable;
import io.ipfs.blockservice.BlockService;
import io.ipfs.exchange.Interface;
import io.ipfs.format.BlockStore;
import io.ipfs.merkledag.DagService;

public class Reader {

    private final DagReader dagReader;
    private final Closeable closeable;


    private Reader(@NonNull Closeable closeable, @NonNull DagReader dagReader) {
        this.closeable = closeable;
        this.dagReader = dagReader;
    }

    public static Reader getReader(@NonNull Closeable closeable, @NonNull BlockStore blockstore,
                                   @NonNull Interface exchange, @NonNull String cid) {
        BlockService blockservice = BlockService.New(blockstore, exchange);
        DagService dags = new DagService(blockservice);
        io.ipfs.format.Node top = Resolver.ResolveNode(closeable, dags, Path.New(cid));
        Objects.requireNonNull(top);
        DagReader dagReader = DagReader.create(top, dags);

        return new Reader(closeable, dagReader);
    }

    public int readNextData(long offset, int size, byte[] data) {
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

    public void seek(long position) {
        dagReader.Seek(closeable, position);
    }

    @Nullable
    public byte[] loadNextData() {
        return this.dagReader.loadNextData(closeable);
    }

    public long getSize() {
        return this.dagReader.getSize();
    }
}
