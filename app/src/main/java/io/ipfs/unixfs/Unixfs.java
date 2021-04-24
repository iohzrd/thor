package io.ipfs.unixfs;


import io.ipfs.format.Node;
import io.ipfs.format.ProtoNode;



public class Unixfs {

    public static byte[] FolderPBData() {

        return unixfs.pb.Unixfs.Data.newBuilder()
                .setType(unixfs.pb.Unixfs.Data.DataType.Directory)
                .build().toByteArray();

    }


    public static ProtoNode EmptyDirNode() {
        byte[] data = FolderPBData();
        return Node.NodeWithData(data);
    }
}
