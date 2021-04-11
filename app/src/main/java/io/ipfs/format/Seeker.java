package io.ipfs.format;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Stack;

import io.core.Closeable;
import io.core.ClosedException;
import io.ipfs.IPFS;
import io.ipfs.cid.Cid;
import io.ipfs.unixfs.FSNode;

public class Seeker {
    private static final String TAG = Seeker.class.getSimpleName();

    @Nullable
    public Cid Next(@NonNull Closeable closeable, @NonNull Stack<Stage> stack) throws ClosedException {

        if (stack.isEmpty()) {
            return null;
        }

        NavigableNode visitedNode = stack.peek().getNode();
        int lastIndex = stack.peek().index();
        lastIndex++;
        int index = lastIndex;
        Node node = NavigableIPLDNode.ExtractIPLDNode(visitedNode);


        if (!(node instanceof ProtoNode)) {
           return null;
        }


        if (node.getLinks().size() > 0) {
            // Internal node, should be a `mdag.ProtoNode` containing a
            // `unixfs.FSNode` (see the `balanced` package for more details).
            FSNode fsNode = FSNode.ExtractFSNode(node);

            // If there aren't enough size hints don't seek
            // (see the `io.EOF` handling error comment below).
            if (fsNode.NumChildren() != node.getLinks().size()) {
                return null;
            }


            // Internal nodes have no data, so just iterate through the
            // sizes of its children (advancing the child index of the
            // `dagWalker`) to find where we need to go down to next in
            // the search

            if (index < fsNode.NumChildren()) {
                stack.peek().setIndex(index);
                long childSize = fsNode.BlockSize(index);

                if (childSize > IPFS.CHUNK_SIZE) { // this is just guessing and might be wrong

                    NavigableNode fetched = visitedNode.FetchChild(closeable, index);
                    stack.push(new Stage(fetched, 0));

                    return Next(closeable, stack);
                }
                return visitedNode.getChild(index);
            } else {
                stack.pop();
                return Next(closeable, stack);
            }
        } else {
            stack.pop();
            return Next(closeable, stack);
        }
    }


}

