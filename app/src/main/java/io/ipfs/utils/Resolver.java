package io.ipfs.utils;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.Closeable;
import io.ipfs.Storage;
import io.ipfs.blockservice.BlockService;
import io.ipfs.cid.Cid;
import io.ipfs.exchange.Interface;
import io.ipfs.format.BlockStore;
import io.ipfs.format.Link;
import io.ipfs.format.Node;
import io.ipfs.format.NodeGetter;
import io.ipfs.format.ProtoNode;
import io.ipfs.merkledag.DagService;
import io.ipfs.unixfs.FSNode;

public class Resolver {

    public static Node resolveNode(@NonNull Closeable closeable, @NonNull Storage storage,
                                  @NonNull Interface exchange, @NonNull String path) {
        BlockStore bs = BlockStore.NewBlockstore(storage);
        BlockService blockservice = BlockService.New(bs, exchange);
        DagService dags = new DagService(blockservice);
        return Resolver.ResolveNode(closeable, dags, Path.New(path));
    }

    public static String resolve(@NonNull Closeable closeable, @NonNull Storage storage,
                                 @NonNull Interface exchange, @NonNull String path) {
        Node resolved = resolveNode(closeable, storage, exchange, path);
        Objects.requireNonNull(resolved);
        return resolved.Cid().String();
    }

    public static Cid ResolvePath(@NonNull Closeable ctx, @NonNull NodeGetter dag, @NonNull Path p) {
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
                                                  @NonNull Path path) {
        Cid cid = ResolvePath(closeable, nodeGetter, path);

        return nodeGetter.Get(closeable, cid);
    }


    public static Pair<Cid, List<String>> ResolveToLastNode(@NonNull Closeable ctx,
                                                            @NonNull NodeGetter dag,
                                                            @NonNull Path path) {
        Pair<Cid, List<String>> result = Path.SplitAbsPath(path);
        Cid c = result.first;
        List<String> p = result.second;

        if (p.size() == 0) {
            return Pair.create(c, Collections.emptyList());
        }

        Node nd = dag.Get(ctx, c);
        if (nd == null) {
            throw new RuntimeException(); // maybe todo
        }


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
                                                        @NonNull NodeGetter nodeGetter, @NonNull Node nd, @NonNull List<String> names) {

        if (nd instanceof ProtoNode) {
            ProtoNode pn = (ProtoNode) nd;
            try {
                FSNode fsn = FSNode.FSNodeFromBytes(pn.getData());
                /*
                if fsn.Type() == ft.THAMTShard {
                    rods:=dag.NewReadOnlyDagService(ds)
                    s, err :=hamt.NewHamtFromDag(rods, nd)
                    if err != nil {
                        return nil,nil, err
                    }

                    out, err :=s.Find(ctx, names[0])
                    if err != nil {
                        return nil,nil, err
                    }

                    return out,names[1:],nil
                }*/
            } catch (Throwable throwable) {
                return nd.ResolveLink(names);
            }
        }
        return nd.ResolveLink(names);
    }


}
