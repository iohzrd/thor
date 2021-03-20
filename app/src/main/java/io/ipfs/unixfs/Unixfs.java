package io.ipfs.unixfs;


import io.ipfs.format.Node;
import io.ipfs.format.ProtoNode;
import io.protos.unixfs.UnixfsProtos;


public class Unixfs {

    public static byte[] FolderPBData() {

        return UnixfsProtos.Data.newBuilder()
                .setType(UnixfsProtos.Data.DataType.Directory)
                .build().toByteArray();

    }


    public static ProtoNode EmptyDirNode() {
        byte[] data = FolderPBData();
        return Node.NodeWithData(data);
    }
}
