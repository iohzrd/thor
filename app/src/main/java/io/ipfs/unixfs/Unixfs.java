package io.ipfs.unixfs;


import io.ipfs.format.Node;
import io.ipfs.format.ProtoNode;


public class Unixfs {

    public static byte[] folderPBData() {

        return unixfs.pb.Unixfs.Data.newBuilder()
                .setType(unixfs.pb.Unixfs.Data.DataType.Directory)
                .build().toByteArray();

    }


    public static ProtoNode emptyDirNode() {
        byte[] data = folderPBData();
        return Node.createNodeWithData(data);
    }
}
