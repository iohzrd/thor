package io.ipfs.unixfs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.ipfs.cid.Builder;
import io.ipfs.format.Node;
import io.ipfs.format.ProtoNode;
import io.protos.unixfs.UnixfsProtos;


public interface Directory {


    static Directory NewDirectory() {

        return new BasicDirectory(Unixfs.EmptyDirNode());
    }

    @Nullable
    static Directory NewDirectoryFromNode(@NonNull Node node) {
        ProtoNode protoNode = (ProtoNode) node;
        FSNode fsNode = FSNode.FSNodeFromBytes(protoNode.getData());

        if (fsNode.Type() == UnixfsProtos.Data.DataType.Directory) {
            return new BasicDirectory((ProtoNode) protoNode.Copy());
        }
        return null;
    }

    void SetCidBuilder(@NonNull Builder cidBuilder);

    Node GetNode();

    void AddChild(@NonNull String name, @NonNull Node link);

    void RemoveChild(@NonNull String name);

    class BasicDirectory implements Directory {
        private final ProtoNode protoNode;

        BasicDirectory(@NonNull ProtoNode protoNode) {
            this.protoNode = protoNode;
        }

        @Override
        public void SetCidBuilder(@NonNull Builder cidBuilder) {
            protoNode.SetCidBuilder(cidBuilder);
        }

        @Override
        public Node GetNode() {
            return protoNode;
        }

        @Override
        public void AddChild(@NonNull String name, @NonNull Node link) {
            protoNode.RemoveNodeLink(name);
            protoNode.AddNodeLink(name, link);
        }

        @Override
        public void RemoveChild(@NonNull String name) {
            protoNode.RemoveNodeLink(name);
        }
    }

}
