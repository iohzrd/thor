package threads.lite.utils;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import threads.lite.bitswap.Interface;
import threads.lite.cid.Cid;
import threads.lite.core.Closeable;
import threads.lite.core.ClosedException;
import threads.lite.dag.BlockService;
import threads.lite.dag.DagService;
import threads.lite.data.Storage;
import threads.lite.format.BlockStore;
import threads.lite.format.Link;
import threads.lite.format.Node;
import threads.lite.format.NodeGetter;


public class Resolver {

    public static Node resolveNode(@NonNull Closeable closeable, @NonNull Storage storage,
                                   @NonNull Interface exchange, @NonNull String path) throws ClosedException {
        BlockStore bs = BlockStore.createBlockStore(storage);
        BlockService blockservice = BlockService.createBlockService(bs, exchange);
        DagService dags = DagService.createDagService(blockservice);
        return Resolver.resolveNode(closeable, dags, Path.create(path));
    }


    public static Cid resolvePath(@NonNull Closeable ctx, @NonNull NodeGetter dag,
                                  @NonNull Path path) throws ClosedException {
        Path ipa = new Path(path.getString());

        List<String> paths = ipa.segments();
        String ident = paths.get(0);
        if (!Objects.equals(ident, "ipfs")) {
            throw new RuntimeException("todo not resolved");
        }

        Pair<Cid, List<String>> resolved = resolveToLastNode(ctx, dag, ipa);

        return resolved.first;

    }

    @Nullable
    public static Node resolveNode(@NonNull Closeable closeable,
                                   @NonNull NodeGetter nodeGetter,
                                   @NonNull Path path) throws ClosedException {
        Cid cid = resolvePath(closeable, nodeGetter, path);
        Objects.requireNonNull(cid);
        return resolveNode(closeable, nodeGetter, cid);
    }

    @Nullable
    public static Node resolveNode(@NonNull Closeable closeable,
                                   @NonNull NodeGetter nodeGetter,
                                   @NonNull Cid cid) throws ClosedException {
        return nodeGetter.getNode(closeable, cid, true);
    }

    public static Pair<Cid, List<String>> resolveToLastNode(@NonNull Closeable closeable,
                                                            @NonNull NodeGetter dag,
                                                            @NonNull Path path) throws ClosedException {
        Pair<Cid, List<String>> result = Path.splitAbsPath(path);
        Cid c = result.first;
        List<String> p = result.second;

        if (p.size() == 0) {
            return Pair.create(c, Collections.emptyList());
        }

        Node node = dag.getNode(closeable, c, true);
        Objects.requireNonNull(node);

        while (p.size() > 0) {

            Pair<Link, List<String>> resolveOnce = node.resolveLink(p);
            Link lnk = resolveOnce.first;
            List<String> rest = resolveOnce.second;

            // Note: have to drop the error here as `ResolveOnce` doesn't handle 'leaf'
            // paths (so e.g. for `echo '{"foo":123}' | ipfs dag put` we wouldn't be
            // able to resolve `zdpu[...]/foo`)
            if (lnk == null) {
                break;
            }

            if (rest.size() == 0) {
                return Pair.create(lnk.getCid(), Collections.emptyList());
            }

            node = lnk.getNode(closeable, dag);
            p = rest;
        }

        if (p.size() == 0) {
            return Pair.create(node.getCid(), Collections.emptyList());
        }

        // Confirm the path exists within the object
        Pair<Object, List<String>> success = node.resolve(p);
        List<String> rest = success.second;
        Object val = success.first;

        if (rest.size() > 0) {
            throw new RuntimeException("path failed to resolve fully");
        }
        if (val instanceof Link) {
            throw new RuntimeException("inconsistent ResolveOnce / nd.Resolve");
        }

        return Pair.create(node.getCid(), p);

    }

}
