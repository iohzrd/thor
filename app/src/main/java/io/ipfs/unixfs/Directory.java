package io.ipfs.unixfs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.ipfs.cid.Builder;
import io.ipfs.format.Node;
import io.ipfs.format.ProtoNode;
import io.ipfs.merkledag.DagService;


public interface Directory {

    static ProtoNode emptyDirNode() {
        byte[] data = unixfs.pb.Unixfs.Data.newBuilder()
                .setType(unixfs.pb.Unixfs.Data.DataType.Directory)
                .build().toByteArray();
        return Node.createNodeWithData(data);
    }
    
    static Directory createDirectory() {
        return new BasicDirectory(emptyDirNode());
    }

    @Nullable
    static Directory createDirectoryFromNode(@NonNull DagService dagService,
                                             @NonNull Node node) {
        ProtoNode protoNode = (ProtoNode) node;
        FSNode fsNode = FSNode.createFSNodeFromBytes(protoNode.getData());

        if (fsNode.Type() == unixfs.pb.Unixfs.Data.DataType.Directory) {
            return new BasicDirectory((ProtoNode) protoNode.copy());
        }
        if (fsNode.Type() == unixfs.pb.Unixfs.Data.DataType.HAMTShard) {
            Shard shard = Hamt.NewHamtFromDag(dagService, node);
            return new HAMTDirectory(shard);
        }
        return null;
    }

    void setCidBuilder(@NonNull Builder cidBuilder);

    Node getNode();

    void addChild(@NonNull String name, @NonNull Node link);

    void removeChild(@NonNull String name);

    class HAMTDirectory implements  Directory {
        private final Shard shard;

        public HAMTDirectory(@NonNull Shard shard) {
            this.shard = shard;
        }

        @Override
        public void setCidBuilder(@NonNull Builder cidBuilder) {
            throw new RuntimeException("not yet supported");
        }

        @Override
        public Node getNode() {
            return shard.Node();
        }

        @Override
        public void addChild(@NonNull String name, @NonNull Node link) {
            throw new RuntimeException("not yet supported");
        }

        @Override
        public void removeChild(@NonNull String name) {
            throw new RuntimeException("not yet supported");
        }
    }

    class BasicDirectory implements Directory {
        private final ProtoNode protoNode;

        BasicDirectory(@NonNull ProtoNode protoNode) {
            this.protoNode = protoNode;
        }

        @Override
        public void setCidBuilder(@NonNull Builder cidBuilder) {
            protoNode.setCidBuilder(cidBuilder);
        }

        @Override
        public Node getNode() {
            return protoNode;
        }

        @Override
        public void addChild(@NonNull String name, @NonNull Node link) {
            protoNode.removeNodeLink(name);
            protoNode.addNodeLink(name, link);
        }

        @Override
        public void removeChild(@NonNull String name) {
            protoNode.removeNodeLink(name);
        }
    }

}
