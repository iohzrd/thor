package io.ipfs.unixfs;

import androidx.annotation.NonNull;

import java.util.Objects;

import io.LogUtils;
import io.ipfs.format.Node;
import io.ipfs.format.ProtoNode;
import io.ipfs.merkledag.DagService;


public class Hamt {
    private static final String TAG = Hamt.class.getSimpleName();

    @NonNull
    public static Shard NewHamtFromDag(@NonNull DagService dagService, @NonNull Node nd) {

        ProtoNode pn = (ProtoNode) nd;
        Objects.requireNonNull(pn);

        FSNode fsn = FSNode.createFSNodeFromBytes(pn.getData());

        if (fsn.Type() != unixfs.pb.Unixfs.Data.DataType.HAMTShard) {
            throw new RuntimeException();
        }

        int size = (int) fsn.fanout();
        Shard ds = Shard.makeShard(dagService, size);
        ds.childer.makeChilder(fsn.getData(), pn.links());
        ds.cid = pn.getCid();
        ds.builder = pn.getCidBuilder();

        return ds;
    }
}
