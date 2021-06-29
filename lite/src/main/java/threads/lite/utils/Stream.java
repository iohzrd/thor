package threads.lite.utils;


import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import threads.lite.bitswap.BitSwap;
import threads.lite.bitswap.Interface;
import threads.lite.cid.Cid;
import threads.lite.cid.Multihash;
import threads.lite.cid.Prefix;
import threads.lite.core.Closeable;
import threads.lite.core.ClosedException;
import threads.lite.dag.Adder;
import threads.lite.dag.BlockService;
import threads.lite.dag.DagService;
import threads.lite.dag.Directory;
import threads.lite.dag.FSNode;
import threads.lite.data.Storage;
import threads.lite.format.BlockStore;
import threads.lite.format.Link;
import threads.lite.format.Node;
import threads.lite.format.ProtoNode;


public class Stream {


    public static Adder getFileAdder(@NonNull Storage storage) {

        BlockStore bs = BlockStore.createBlockStore(storage);
        Interface exchange = new Exchange(bs);
        BlockService blockservice = BlockService.createBlockService(bs, exchange);
        DagService dagService = DagService.createDagService(blockservice);
        Adder fileAdder = Adder.NewAdder(dagService);

        Prefix prefix = Node.PrefixForCidVersion(1);

        prefix.MhType = Multihash.Type.sha2_256.index;
        prefix.MhLength = -1;

        fileAdder.RawLeaves = false;
        fileAdder.builder = prefix;


        return fileAdder;
    }


    public static void removeCid(@NonNull Closeable closeable,
                                 @NonNull Storage storage,
                                 @NonNull Cid cid) throws ClosedException {

        BlockStore bs = BlockStore.createBlockStore(storage);
        Interface exchange = new Exchange(bs);
        BlockService blockservice = BlockService.createBlockService(bs, exchange);
        DagService dags = DagService.createDagService(blockservice);
        Node top = Resolver.resolveNode(closeable, dags, cid);
        Objects.requireNonNull(top);

        RefWriter rw = new RefWriter(true, -1);
        rw.evalRefs(top);
        List<Cid> cids = new ArrayList<>(rw.getCids());

        cids.add(top.getCid());
        bs.deleteBlocks(cids);

    }

    public static boolean isDir(@NonNull Closeable closeable,
                                @NonNull BlockStore blockstore,
                                @NonNull BitSwap exchange,
                                @NonNull Cid cid) throws ClosedException {


        BlockService blockservice = BlockService.createBlockService(blockstore, exchange);
        DagService dagService = DagService.createDagService(blockservice);

        threads.lite.format.Node node = Resolver.resolveNode(closeable, dagService, cid);
        Objects.requireNonNull(node);
        Directory dir = Directory.createDirectoryFromNode(node);
        return dir != null;
    }

    public static Cid createEmptyDir(@NonNull Storage storage) {

        Adder fileAdder = getFileAdder(storage);

        Node nd = fileAdder.CreateEmptyDir();
        return nd.getCid();
    }


    public static Cid addLinkToDir(@NonNull Storage storage, @NonNull Closeable closeable,
                                   @NonNull Cid dir, @NonNull String name, @NonNull Cid link) throws ClosedException {

        Adder fileAdder = getFileAdder(storage);

        BlockStore bs = BlockStore.createBlockStore(storage);
        Interface exchange = new Exchange(bs);
        BlockService blockservice = BlockService.createBlockService(bs, exchange);
        DagService dagService = DagService.createDagService(blockservice);

        threads.lite.format.Node dirNode = Resolver.resolveNode(closeable, dagService, dir);
        Objects.requireNonNull(dirNode);
        threads.lite.format.Node linkNode = Resolver.resolveNode(closeable, dagService, link);
        Objects.requireNonNull(linkNode);
        Node nd = fileAdder.AddLinkToDir(dirNode, name, linkNode);
        return nd.getCid();

    }

    public static Cid removeLinkFromDir(@NonNull Storage storage, @NonNull Closeable closeable,
                                        @NonNull Cid dir, @NonNull String name) throws ClosedException {

        Adder fileAdder = getFileAdder(storage);

        BlockStore bs = BlockStore.createBlockStore(storage);
        Interface exchange = new Exchange(bs);
        BlockService blockservice = BlockService.createBlockService(bs, exchange);
        DagService dagService = DagService.createDagService(blockservice);

        threads.lite.format.Node dirNode = Resolver.resolveNode(closeable, dagService, dir);
        Objects.requireNonNull(dirNode);
        Node nd = fileAdder.RemoveChild(dirNode, name);
        return nd.getCid();

    }

    public static void ls(@NonNull LinkCloseable closeable, @NonNull BlockStore blockstore,
                          @NonNull Interface exchange,
                          @NonNull Cid cid, boolean resolveChildren) throws ClosedException {

        BlockService blockservice = BlockService.createBlockService(blockstore, exchange);
        DagService dagService = DagService.createDagService(blockservice);


        threads.lite.format.Node node = Resolver.resolveNode(closeable, dagService, cid);
        Objects.requireNonNull(node);
        Directory dir = Directory.createDirectoryFromNode(node);

        if (dir == null) {
            lsFromLinks(closeable, dagService, node.getLinks(), resolveChildren);
        } else {
            lsFromLinksAsync(closeable, dagService, dir, resolveChildren);
        }

    }


    @NonNull
    public static Cid write(@NonNull Storage storage, @NonNull WriterStream writerStream) {

        Adder fileAdder = getFileAdder(storage);
        Node node = fileAdder.AddReader(writerStream);
        return node.getCid();
    }

    private static void lsFromLinksAsync(@NonNull LinkCloseable closeable,
                                         @NonNull DagService dagService,
                                         @NonNull Directory dir,
                                         boolean resolveChildren) throws ClosedException {

        List<Link> links = dir.getNode().getLinks();
        for (Link link : links) {
            processLink(closeable, dagService, link, resolveChildren);
        }
    }

    private static void lsFromLinks(@NonNull LinkCloseable closeable,
                                    @NonNull DagService dagService,
                                    @NonNull List<Link> links,
                                    boolean resolveChildren) throws ClosedException {
        for (Link link : links) {
            processLink(closeable, dagService, link, resolveChildren);
        }
    }

    private static void processLink(@NonNull LinkCloseable closeable,
                                    @NonNull DagService dagService,
                                    @NonNull Link link,
                                    boolean resolveChildren) throws ClosedException {

        String name = link.getName();
        String hash = link.getCid().String();
        long size = link.getSize();
        Cid cid = link.getCid();

        if (cid.getType() == Cid.Raw) {
            closeable.info(threads.lite.utils.Link.create(name, hash, size, threads.lite.utils.Link.File));
        } else if (cid.getType() == Cid.DagProtobuf) {
            if (!resolveChildren) {
                closeable.info(threads.lite.utils.Link.create(name, hash, size, threads.lite.utils.Link.NotKnown));
            } else {

                Node linkNode = link.getNode(closeable, dagService);
                if (linkNode instanceof ProtoNode) {
                    ProtoNode pn = (ProtoNode) linkNode;
                    FSNode d = FSNode.createFSNodeFromBytes(pn.getData());
                    int type;
                    switch (d.Type()) {
                        case File:
                            type = threads.lite.utils.Link.File;
                            break;
                        case Raw:
                            type = threads.lite.utils.Link.Raw;
                            break;
                        case Symlink:
                            type = threads.lite.utils.Link.Symlink;
                            break;
                        case HAMTShard:
                        case Directory:
                        case Metadata:
                        default:
                            type = threads.lite.utils.Link.Dir;
                    }
                    size = d.getFileSize();
                    closeable.info(threads.lite.utils.Link.create(name, hash, size, type));

                }
            }
        }
    }
}
