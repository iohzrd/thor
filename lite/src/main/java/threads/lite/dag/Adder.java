package threads.lite.dag;


import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Objects;

import threads.lite.IPFS;
import threads.lite.cid.Builder;
import threads.lite.format.Node;
import threads.lite.format.Reader;
import threads.lite.utils.Splitter;
import threads.lite.utils.WriterStream;

public class Adder {
    @NonNull
    private final DagService dagService;
    public boolean RawLeaves;
    public Builder builder;

    private Adder(@NonNull DagService dagService) {
        this.dagService = dagService;
    }

    public static Adder NewAdder(@NonNull DagService dagService) {
        return new Adder(dagService);
    }


    public Node CreateEmptyDir() {
        Directory dir = Directory.createDirectory();
        dir.setCidBuilder(builder);
        Node fnd = dir.getNode();
        dagService.Add(fnd);
        return fnd;
    }

    public Node AddLinkToDir(@NonNull Node dirNode, @NonNull String name, @NonNull Node link) {
        Directory dir = Directory.createDirectoryFromNode(dirNode);
        Objects.requireNonNull(dir);
        dir.setCidBuilder(builder);
        dir.addChild(name, link);
        Node fnd = dir.getNode();
        dagService.Add(fnd);
        return fnd;
    }

    public Node RemoveChild(@NonNull Node dirNode, @NonNull String name) {
        Directory dir = Directory.createDirectoryFromNode(dirNode);
        Objects.requireNonNull(dir);
        dir.setCidBuilder(builder);
        dir.removeChild(name);
        Node fnd = dir.getNode();
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
                int read = reader.read(buf);
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
                return reader.done();
            }
        };

        DagBuilderHelper db = new DagBuilderHelper(
                dagService, builder, splitter, RawLeaves);

        return Trickle.Layout(db);
    }

}
