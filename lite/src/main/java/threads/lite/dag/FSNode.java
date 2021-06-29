package threads.lite.dag;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import threads.lite.format.Node;
import threads.lite.format.ProtoNode;
import threads.lite.format.RawNode;


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


    public static FSNode createFSNode(@NonNull unixfs.pb.Unixfs.Data.DataType dataType) {
        return new FSNode(dataType);
    }

    public static FSNode createFSNodeFromBytes(byte[] data) {
        return new FSNode(data);
    }

    public static byte[] readUnixFSNodeData(@NonNull Node node) {

        if (node instanceof ProtoNode) {
            FSNode fsNode = createFSNodeFromBytes(node.getData());
            switch (fsNode.Type()) {
                case File:
                case Raw:
                    return fsNode.getData();
                default:
                    throw new RuntimeException("found %s node in unexpected place " +
                            fsNode.Type().name());
            }
        } else if (node instanceof RawNode) {
            return node.getRawData();
        } else {
            throw new RuntimeException("not supported type");
        }

    }

    public static FSNode extractFSNode(@NonNull Node node) {
        if (node instanceof ProtoNode) {
            return createFSNodeFromBytes(node.getData());
        }
        throw new RuntimeException("expected a ProtoNode as internal node");

    }

    private void updateFileSize(long fileSize) {
        long previous = data.getFilesize();
        data = data.toBuilder().setFilesize(previous + fileSize).build();
    }

    public byte[] getData() {
        return data.getData().toByteArray();
    }

    public void setData(byte[] bytes) {
        updateFileSize(bytes.length - getData().length);
        data = data.toBuilder().setData(ByteString.copyFrom(bytes)).build();
    }

    public unixfs.pb.Unixfs.Data.DataType Type() {
        return data.getType();
    }

    public long getFileSize() {
        return data.getFilesize();
    }

    public long getBlockSize(int i) {
        return data.getBlocksizes(i);
    }

    public int numChildren() {
        return data.getBlocksizesCount();
    }

    public void addBlockSize(long size) {
        updateFileSize(size);
        data = data.toBuilder().addBlocksizes(size).build();
    }

    public byte[] getBytes() {
        return data.toByteArray();

    }

    public long fanout() {
        return data.getFanout();
    }
}


