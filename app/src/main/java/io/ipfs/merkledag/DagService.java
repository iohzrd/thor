package io.ipfs.merkledag;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.Closeable;
import io.ipfs.blockservice.BlockService;
import io.ipfs.cid.Cid;
import io.ipfs.format.Block;
import io.ipfs.format.Decoder;
import io.ipfs.format.Node;
import io.ipfs.format.NodeAdder;
import io.ipfs.format.NodeGetter;

public class DagService implements NodeGetter, NodeAdder {
    private final BlockService blockservice;

    public DagService(@NonNull BlockService blockService) {
        this.blockservice = blockService;
    }

    @Override
    @Nullable
    public Node Get(@NonNull Closeable closeable, @NonNull Cid cid) {

        Block b = blockservice.GetBlock(closeable, cid);
        if (b == null) {
            return null;
        }
        return Decoder.Decode(b);
    }


    public void Add(@NonNull Node nd) {
        blockservice.AddBlock(nd);
    }

}
