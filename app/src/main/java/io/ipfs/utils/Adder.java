package io.ipfs.utils;


import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Objects;

import io.ipfs.IPFS;
import io.ipfs.cid.Builder;
import io.ipfs.format.Node;
import io.ipfs.format.Reader;
import io.ipfs.merkledag.DagBuilderHelper;
import io.ipfs.merkledag.DagService;
import io.ipfs.unixfs.Directory;
import io.ipfs.unixfs.Trickle;

public class Adder {
    @NonNull
    private final DagService dagService;
    public boolean RawLeaves;
    public Builder CidBuilder;

    private Adder(@NonNull DagService dagService) {
        this.dagService = dagService;
    }

    public static Adder NewAdder(@NonNull DagService dagService) {
        return new Adder(dagService);
    }


    public Node CreateEmptyDir() {
        Directory dir = Directory.NewDirectory();
        dir.SetCidBuilder(CidBuilder);
        Node fnd = dir.GetNode();
        dagService.Add(fnd);
        return fnd;
    }

    public Node AddLinkToDir(@NonNull Node dirNode, @NonNull String name, @NonNull Node link) {
        Directory dir = Directory.NewDirectoryFromNode(dirNode);
        Objects.requireNonNull(dir);
        dir.SetCidBuilder(CidBuilder);
        dir.AddChild(name, link);
        Node fnd = dir.GetNode();
        dagService.Add(fnd);
        return fnd;
    }

    public Node RemoveChild(@NonNull Node dirNode, @NonNull String name) {
        Directory dir = Directory.NewDirectoryFromNode(dirNode);
        Objects.requireNonNull(dir);
        dir.SetCidBuilder(CidBuilder);
        dir.RemoveChild(name);
        Node fnd = dir.GetNode();
        dagService.Add(fnd);
        return fnd;
    }


    @NonNull
    public Node AddReader(@NonNull final WriterStream reader) {

        Splitter splitter = new Splitter() {

            @Override
            public Reader Reader() {
                return reader;
            }

            @Override
            public byte[] NextBytes() {

                int size = IPFS.CHUNK_SIZE;
                byte[] buf = new byte[size];
                int read = reader.Read(buf);
                if (read < 0) {
                    return null;
                } else if (read < size) {
                    return Arrays.copyOfRange(buf, 0, read);
                } else {
                    return buf;
                }
            }

            @Override
            public boolean Done() {
                return reader.Done();
            }
        };

        DagBuilderHelper db = new DagBuilderHelper(
                dagService, CidBuilder, splitter, RawLeaves);

        return Trickle.Layout(db);
    }

}
