package threads.lite.dag;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

import threads.lite.IPFS;
import threads.lite.cid.Cid;
import threads.lite.core.Closeable;
import threads.lite.core.ClosedException;
import threads.lite.format.NavigableIPLDNode;
import threads.lite.format.NavigableNode;
import threads.lite.format.Node;
import threads.lite.format.NodeGetter;
import threads.lite.format.ProtoNode;
import threads.lite.format.RawNode;
import threads.lite.format.Seeker;
import threads.lite.format.Stage;
import threads.lite.format.Visitor;
import threads.lite.format.Walker;

public class DagReader {

    public final AtomicInteger atomicLeft = new AtomicInteger(0);
    private final long size;
    private final Visitor visitor;
    private final Walker dagWalker;
    private final NodeGetter nodeGetter;
    private final Set<Cid> cids = new HashSet<>();

    public DagReader(@NonNull Walker dagWalker, @NonNull NodeGetter nodeGetter, long size) {
        this.dagWalker = dagWalker;
        this.nodeGetter = nodeGetter;
        this.size = size;
        this.visitor = new Visitor(dagWalker.getRoot());

    }

    public static DagReader create(@NonNull Node node, @NonNull NodeGetter serv) {
        long size = 0;


        if (node instanceof RawNode) {
            size = node.getData().length;
        } else if (node instanceof ProtoNode) {
            FSNode fsNode = FSNode.createFSNodeFromBytes(node.getData());

            switch (fsNode.Type()) {
                case Raw:
                case File:
                    size = fsNode.getFileSize();
                    break;
            }
        } else {
            throw new RuntimeException("type not supported");
        }

        Walker dagWalker = Walker.NewWalker(NavigableIPLDNode.NewNavigableIPLDNode(node, serv));
        return new DagReader(dagWalker, serv, size);

    }

    public long getSize() {
        return size;
    }

    public void Seek(@NonNull Closeable closeable, long offset) throws ClosedException {
        Pair<Stack<Stage>, Long> result = dagWalker.seek(closeable, offset);
        this.atomicLeft.set(result.second.intValue());
        this.visitor.reset(result.first);
    }

    @Nullable
    public byte[] loadNextData(@NonNull Closeable closeable) throws ClosedException {


        int left = atomicLeft.getAndSet(0);
        if (left > 0) {
            NavigableNode navigableNode = visitor.peekStage().getNode();

            Node node = NavigableIPLDNode.extractIPLDNode(navigableNode);

            if (node.getLinks().size() == 0) {

                byte[] data = FSNode.readUnixFSNodeData(node);

                return Arrays.copyOfRange(data, left, data.length);
            }
        }

        while (true) {
            NavigableNode visitedNode = dagWalker.next(closeable, visitor);
            if (visitedNode == null) {
                return null;
            }

            Node node = NavigableIPLDNode.extractIPLDNode(visitedNode);
            if (node.getLinks().size() > 0) {
                continue;
            }

            return FSNode.readUnixFSNodeData(node);
        }

    }

    public void preloadData(@NonNull Closeable closeable) throws ClosedException {

        List<Cid> preloads = new ArrayList<>();
        Seeker seeker = new Seeker();
        Stack<Stage> stack = visitor.copy();
        int runs = 0;

        int min = (cids.size() / IPFS.PRELOAD) + IPFS.PRELOAD_DIST;

        int maxRuns = Math.min(min, IPFS.PRELOAD);

        while (runs < maxRuns) {
            Cid cid = seeker.next(closeable, stack);
            if (cid == null) {
                break;
            }
            if (!cids.contains(cid)) {
                preloads.add(cid);
            }
            runs++;
        }

        if (preloads.size() >= IPFS.PRELOAD_DIST) {
            cids.addAll(preloads);
            nodeGetter.preload(closeable, preloads);
        }

    }
}
