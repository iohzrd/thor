package threads.lite.dag;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import threads.lite.cid.Cid;
import threads.lite.core.Closeable;
import threads.lite.core.ClosedException;
import threads.lite.format.Block;
import threads.lite.format.Decoder;
import threads.lite.format.Node;
import threads.lite.format.NodeAdder;
import threads.lite.format.NodeGetter;

public interface DagService extends NodeGetter, NodeAdder {

    static DagService createReadOnlyDagService(@NonNull NodeGetter nodeGetter) {
        return new DagService() {
            @Nullable

            @Override
            public Node getNode(@NonNull Closeable closeable, @NonNull Cid cid, boolean root) throws ClosedException {
                return nodeGetter.getNode(closeable, cid, root);
            }

            @Override
            public void preload(@NonNull Closeable ctx, @NonNull List<Cid> cids) {
                // nothing to do here
            }

            @Override
            public void Add(@NonNull Node nd) {
                // nothing to do here
            }
        };
    }

    static DagService createDagService(@NonNull BlockService blockService) {
        return new DagService() {

            @Override
            @Nullable
            public Node getNode(@NonNull Closeable closeable, @NonNull Cid cid, boolean root) throws ClosedException {

                Block b = blockService.getBlock(closeable, cid, root);
                if (b == null) {
                    return null;
                }
                return Decoder.Decode(b);
            }

            @Override
            public void preload(@NonNull Closeable closeable, @NonNull List<Cid> cids) {
                blockService.preload(closeable, cids);
            }

            public void Add(@NonNull Node nd) {
                blockService.addBlock(nd);
            }
        };
    }


}
