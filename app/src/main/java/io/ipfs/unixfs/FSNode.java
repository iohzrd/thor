package io.ipfs.unixfs;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import io.ipfs.format.Node;
import io.ipfs.format.ProtoNode;
import io.ipfs.format.RawNode;


public class FSNode {
    private unixfs.pb.Unixfs.Data data;

    private FSNode(@NonNull unixfs.pb.Unixfs.Data.DataType dataType) {
        data = unixfs.pb.Unixfs.Data.newBuilder().setType(dataType).
                setFilesize(0L).build();
    }

    private FSNode(byte[] content) {
        try {
            data = unixfs.pb.Unixfs.Data.parseFrom(content);
        } catch (Throwable throwable) {
            throw new RuntimeException();
        }
    }


    public static FSNode NewFSNode(@NonNull unixfs.pb.Unixfs.Data.DataType dataType) {
        return new FSNode(dataType);
    }

    public static FSNode FSNodeFromBytes(byte[] data) {
        return new FSNode(data);
    }

    public static byte[] ReadUnixFSNodeData(@NonNull Node node) {

        if (node instanceof ProtoNode) {
            FSNode fsNode = FSNodeFromBytes(node.getData());
            switch (fsNode.Type()) {
                case File:
                case Raw:
                    return fsNode.Data();
                default:
                    throw new RuntimeException("found %s node in unexpected place " +
                            fsNode.Type().name());
            }
        } else if (node instanceof RawNode) {
            return node.RawData();
        } else {
            throw new RuntimeException("not supported type");
        }

    }

    public static FSNode ExtractFSNode(@NonNull Node node) {
        if (node instanceof ProtoNode) {
            return FSNodeFromBytes(node.getData());
        }
        throw new RuntimeException("expected a ProtoNode as internal node");

    }

    private void UpdateFilesize(long filesize) {
        long previous = data.getFilesize();
        data = data.toBuilder().setFilesize(previous + filesize).build();
    }

    public byte[] Data() {
        return data.getData().toByteArray();
    }

    public unixfs.pb.Unixfs.Data.DataType Type() {
        return data.getType();
    }

    public long FileSize() {
        return data.getFilesize();
    }

    public long BlockSize(int i) {
        return data.getBlocksizes(i);
    }

    public int NumChildren() {
        return data.getBlocksizesCount();
    }


    public void AddBlockSize(long size) {
        UpdateFilesize(size);
        data = data.toBuilder().addBlocksizes(size).build();
    }

    public byte[] GetBytes() {
        return data.toByteArray();

    }

    public void SetData(byte[] bytes) {
        UpdateFilesize(bytes.length - Data().length);
        data = data.toBuilder().setData(ByteString.copyFrom(bytes)).build();
    }

    public long Fanout() {
        return data.getFanout();
    }
}


