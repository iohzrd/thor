package io.ipfs.utils;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.ipfs.blockservice.BlockService;
import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.datastore.Storage;
import io.ipfs.exchange.Interface;
import io.ipfs.format.BlockStore;
import io.ipfs.format.Link;
import io.ipfs.format.Node;
import io.ipfs.format.NodeGetter;
import io.ipfs.format.ProtoNode;
import io.ipfs.merkledag.DagService;
import io.ipfs.unixfs.FSNode;
import io.ipfs.unixfs.Hamt;
import io.ipfs.unixfs.Shard;


public class Resolver {

    public static Node resolveNode(@NonNull Closeable closeable, @NonNull Storage storage,
                                   @NonNull Interface exchange, @NonNull String path) throws ClosedException {
        BlockStore bs = BlockStore.NewBlockstore(storage);
        BlockService blockservice = BlockService.New(bs, exchange);
        DagService dags = DagService.createDagService(blockservice);
        return Resolver.ResolveNode(closeable, dags, Path.New(path));
    }

    public static String resolve(@NonNull Closeable closeable, @NonNull Storage storage,
                                 @NonNull Interface exchange, @NonNull String path) throws ClosedException {
        Node resolved = resolveNode(closeable, storage, exchange, path);
        Objects.requireNonNull(resolved);
        return resolved.Cid().String();
    }

    public static Cid ResolvePath(@NonNull Closeable ctx, @NonNull NodeGetter dag, @NonNull Path p) throws ClosedException {
        Path ipa = new Path(p.String());

        List<String> paths = ipa.Segments();
        String ident = paths.get(0);
        if (!Objects.equals(ident, "ipfs")) {
            throw new RuntimeException("todo not resolved");
        }

        Pair<Cid, List<String>> resolved = ResolveToLastNode(ctx, dag, ipa);

        return resolved.first;

    }

    @Nullable
    public static io.ipfs.format.Node ResolveNode(@NonNull Closeable closeable,
                                                  @NonNull NodeGetter nodeGetter,
                                                  @NonNull Path path) throws ClosedException {
        Cid cid = ResolvePath(closeable, nodeGetter, path);

        return nodeGetter.Get(closeable, cid);
    }

    public static Pair<Cid, List<String>> ResolveToLastNode(@NonNull Closeable ctx,
                                                            @NonNull NodeGetter dag,
                                                            @NonNull Path path) throws ClosedException {
        Pair<Cid, List<String>> result = Path.SplitAbsPath(path);
        Cid c = result.first;
        List<String> p = result.second;

        if (p.size() == 0) {
            return Pair.create(c, Collections.emptyList());
        }

        Node nd = dag.Get(ctx, c);
        Objects.requireNonNull(nd);

        while (p.size() > 0) {

            Pair<Link, List<String>> resolveOnce = ResolveOnce(ctx, dag, nd, p);
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

            nd = lnk.GetNode(ctx, dag);
            p = rest;
        }

        if (p.size() == 0) {
            return Pair.create(nd.Cid(), Collections.emptyList());
        }

        // Confirm the path exists within the object
        Pair<Object, List<String>> success = nd.Resolve(p);
        List<String> rest = success.second;
        Object val = success.first;

        if (rest.size() > 0) {
            throw new RuntimeException("path failed to resolve fully");
        }
        if (val instanceof Link) {
            throw new RuntimeException("inconsistent ResolveOnce / nd.Resolve");
        }

        return Pair.create(nd.Cid(), p);

    }

    private static Pair<Link, List<String>> ResolveOnce(@NonNull Closeable closeable,
                                                        @NonNull NodeGetter nodeGetter,
                                                        @NonNull Node nd,
                                                        @NonNull List<String> names) {

        if (nd instanceof ProtoNode) {
            ProtoNode pn = (ProtoNode) nd;
            try {
                FSNode fsn = FSNode.FSNodeFromBytes(pn.getData());

                if (fsn.Type() == unixfs.pb.Unixfs.Data.DataType.HAMTShard) {

                    DagService rods = DagService.createReadOnlyDagService(nodeGetter);
                    Shard s = Hamt.NewHamtFromDag(rods, nd);
                    Link link = s.Find(closeable, names.get(0));
                    return Pair.create(link, names.subList(1, names.size()));

                }
            } catch (Throwable throwable) {
                return nd.ResolveLink(names);
            }
        }
        return nd.ResolveLink(names);
    }


}
