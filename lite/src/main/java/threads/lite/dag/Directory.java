package threads.lite.dag;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import threads.lite.cid.Builder;
import threads.lite.format.Node;
import threads.lite.format.ProtoNode;


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
    static Directory createDirectoryFromNode(@NonNull Node node) {
        ProtoNode protoNode = (ProtoNode) node;
        FSNode fsNode = FSNode.createFSNodeFromBytes(protoNode.getData());

        if (fsNode.Type() == unixfs.pb.Unixfs.Data.DataType.Directory ||
                fsNode.Type() == unixfs.pb.Unixfs.Data.DataType.HAMTShard) {
            return new BasicDirectory((ProtoNode) protoNode.copy());
        }
        return null;
    }

    void setCidBuilder(@NonNull Builder cidBuilder);

    Node getNode();

    void addChild(@NonNull String name, @NonNull Node link);

    void removeChild(@NonNull String name);

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
