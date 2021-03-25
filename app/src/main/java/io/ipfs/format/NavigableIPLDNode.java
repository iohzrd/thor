package io.ipfs.format;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.Closeable;
import io.LogUtils;
import io.ipfs.ClosedException;
import io.ipfs.IPFS;
import io.ipfs.cid.Cid;

public class NavigableIPLDNode implements NavigableNode {
    private static final String TAG = NavigableIPLDNode.class.getSimpleName();

    private final Node node;
    private final NodeGetter nodeGetter;
    private final List<Cid> cids = new ArrayList<>();
    private int preLoader = -1;

    private NavigableIPLDNode(@NonNull Node node, @NonNull NodeGetter nodeGetter) {
        this.node = node;
        this.nodeGetter = nodeGetter;
        fillLinkCids(node);
    }

    public static NavigableIPLDNode NewNavigableIPLDNode(
            @NonNull Node node, @NonNull NodeGetter nodeGetter) {
        return new NavigableIPLDNode(node, nodeGetter);
    }

    public static Node ExtractIPLDNode(@NonNull NavigableNode node) {
        if (node instanceof NavigableIPLDNode) {
            NavigableIPLDNode navigableIPLDNode = (NavigableIPLDNode) node;
            return navigableIPLDNode.GetIPLDNode();
        }
        throw new RuntimeException("not expected behaviour");
    }

    public Node GetIPLDNode() {
        return node;
    }

    private void fillLinkCids(@NonNull Node node) {
        List<Link> links = node.getLinks();

        for (Link link : links) {
            cids.add(link.getCid());
        }
    }

    @Override
    public NavigableNode FetchChild(@NonNull Closeable ctx, int childIndex) throws ClosedException {
        Node child = getPromiseValue(ctx, childIndex);
        Objects.requireNonNull(child);

        try {
            int value = childIndex / IPFS.PRELOAD;
            if (value > preLoader) {
                preLoader = value;
                int min = Math.min((value * IPFS.PRELOAD) + 1, cids.size());
                int max = Math.min((value * IPFS.PRELOAD) + IPFS.PRELOAD + IPFS.PRELOAD_DIST, cids.size());
                int dist = max - min;
                if (dist >= IPFS.PRELOAD_DIST && min < cids.size()) {
                    LogUtils.error(TAG, "min " + min + " max " + max +
                            " childIndex " + childIndex + " length " + cids.size());
                    nodeGetter.Load(ctx, cids.subList(min, max));
                }
            }
        } catch (Throwable throwable){
            LogUtils.error(TAG, throwable);
        }

        return NewNavigableIPLDNode(child, nodeGetter);

    }


    @Override
    public int ChildTotal() {
        return GetIPLDNode().getLinks().size();
    }

    private Node getPromiseValue(Closeable ctx, int childIndex) throws ClosedException {
        return nodeGetter.Get(ctx, cids.get(childIndex));

    }

    @NonNull
    @Override
    public String toString() {
        return node.toString() + " (" + ChildTotal() + ") ";
    }
}
