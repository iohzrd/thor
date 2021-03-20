package io.ipfs.format;

import androidx.annotation.NonNull;

import io.ipfs.cid.Cid;

public class Decoder {
    @NonNull
    public static Node Decode(@NonNull Block block) {

        if (block instanceof Node) {
            return (Node) block;
        }

        long type = block.Cid().Type();

        if (type == Cid.DagProtobuf) {
            return DecodeProtobufBlock(block);
        } else if (type == Cid.Raw) {
            return DecodeRawBlock(block);
        } else if (type == Cid.DagCBOR) {
            throw new RuntimeException("Not supported decoder");
        } else {
            throw new RuntimeException("Not supported decoder");
        }
    }


    public static Node DecodeRawBlock(@NonNull Block block) {
        if (block.Cid().Type() != Cid.Raw) {
            throw new RuntimeException("raw nodes cannot be decoded from non-raw blocks");
        }
        return new RawNode(block);
    }

    public static Node DecodeProtobufBlock(@NonNull Block b) {
        Cid c = b.Cid();
        if (c.Type() != Cid.DagProtobuf) {
            throw new RuntimeException("this function can only decode protobuf nodes");
        }

        ProtoNode decnd = DecodeProtobuf(b.RawData());
        decnd.cached = c;
        // TODO decnd.builder = c.Prefix();
        return decnd;
    }

    public static ProtoNode DecodeProtobuf(byte[] encoded) {
        ProtoNode n = new ProtoNode();
        n.unmarshal(encoded);
        return n;
    }
}
