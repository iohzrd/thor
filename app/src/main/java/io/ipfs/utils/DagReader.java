package io.ipfs.utils;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

import io.Closeable;
import io.ipfs.format.NavigableIPLDNode;
import io.ipfs.format.NavigableNode;
import io.ipfs.format.Node;
import io.ipfs.format.NodeGetter;
import io.ipfs.format.ProtoNode;
import io.ipfs.format.RawNode;
import io.ipfs.format.Stage;
import io.ipfs.format.Visitor;
import io.ipfs.format.Walker;
import io.ipfs.unixfs.FSNode;

public class DagReader {
    private static final String TAG = DagReader.class.getSimpleName();

    private final long size;
    private final Visitor visitor;

    private final Walker dagWalker;
    public final AtomicInteger atomicLeft = new AtomicInteger(0);

    public DagReader(@NonNull Walker dagWalker, long size) {
        this.dagWalker = dagWalker;
        this.size = size;
        this.visitor = new Visitor(dagWalker.getRoot());

    }

    public static DagReader create(@NonNull Node node, @NonNull NodeGetter serv) {
        long size = 0;


        if (node instanceof RawNode) {
            size = node.getData().length;
        } else if (node instanceof ProtoNode) {
            FSNode fsNode = FSNode.FSNodeFromBytes(node.getData());

            switch (fsNode.Type()) {
                case Raw:
                case File:
                    size = fsNode.FileSize();
                    break;
            }
        } else {
            throw new RuntimeException("type not supported");
        }

        Walker dagWalker = Walker.NewWalker(NavigableIPLDNode.NewNavigableIPLDNode(node, serv));
        return new DagReader(dagWalker, size);

    }

    public long getSize() {
        return size;
    }


    public void Seek(@NonNull Closeable closeable, long offset) {
        Pair<Stack<Stage>, Long> result = dagWalker.Seek(closeable, offset);
        this.atomicLeft.set(result.second.intValue());
        this.visitor.reset(result.first);
    }

    @Nullable
    public byte[] loadNextData(@NonNull Closeable closeable) {


        int left = atomicLeft.getAndSet(0);
        if (left > 0) {
            NavigableNode navigableNode = visitor.peekStage().getNode();

            Node node = NavigableIPLDNode.ExtractIPLDNode(navigableNode);

            if (node.getLinks().size() == 0) {

                byte[] data = FSNode.ReadUnixFSNodeData(node);

                return Arrays.copyOfRange(data, left, data.length);
            }
        }

        while (true) {
            NavigableNode visitedNode = dagWalker.Next(closeable, visitor);
            if (visitedNode == null) {
                return null;
            }

            Node node = NavigableIPLDNode.ExtractIPLDNode(visitedNode);
            if (node.getLinks().size() > 0) {
                continue;
            }

            return FSNode.ReadUnixFSNodeData(node);
        }

    }
}
