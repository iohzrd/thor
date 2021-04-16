package io.ipfs.utils;


import androidx.annotation.NonNull;

import com.google.common.primitives.Bytes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import io.core.Closeable;
import io.core.ClosedException;
import io.dht.ResolveInfo;
import io.dht.Routing;
import io.ipfs.IPFS;
import io.ipfs.blockservice.BlockService;
import io.ipfs.cid.Cid;
import io.ipfs.cid.Prefix;
import io.ipfs.datastore.Storage;
import io.ipfs.exchange.Interface;
import io.ipfs.format.BlockStore;
import io.ipfs.format.Link;
import io.ipfs.format.Node;
import io.ipfs.format.ProtoNode;
import io.ipfs.merkledag.DagService;
import io.ipfs.multihash.Multihash;
import io.ipfs.offline.Exchange;
import io.ipfs.unixfs.Directory;
import io.ipfs.unixfs.FSNode;
import io.ipns.Ipns;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import io.protos.ipns.IpnsProtos;


public class Stream {

    private static final String TAG = Stream.class.getSimpleName();
    private static Duration DefaultRecordEOL = Duration.ofHours(24);


    public static String base32(@NonNull PeerId peerId) {
        try {
            // only support fromBase58
            Multihash multihash = Multihash.fromBase58(peerId.toBase58());
            return Cid.NewCidV1(Cid.Libp2pKey, multihash.toBytes()).String();
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public static void ResolveName(@NonNull Closeable closeable,
                                   @NonNull Routing routing,
                                   @NonNull ResolveInfo info,
                                   @NonNull PeerId id,
                                   int dhtRecords) throws ClosedException {


        byte[] ipns = IPFS.IPNS_PATH.getBytes();
        byte[] ipnsKey = Bytes.concat(ipns, id.getBytes());
        routing.SearchValue(closeable, info, ipnsKey, dhtRecords);

    }

    public static void PublishName(@NonNull Closeable closable, @NonNull Routing routing,
                                   @NonNull PrivKey privKey, @NonNull String path,
                                   @NonNull PeerId id, int sequence) throws ClosedException {


        Date eol = Date.from(new Date().toInstant().plus(DefaultRecordEOL));

        IpnsProtos.IpnsEntry
                record = Ipns.Create(privKey, path.getBytes(), sequence, eol);

        PubKey pk = privKey.publicKey();

        record = Ipns.EmbedPublicKey(pk, record);

        byte[] bytes = record.toByteArray();

        byte[] ipns = IPFS.IPNS_PATH.getBytes();
        byte[] ipnsKey = Bytes.concat(ipns, id.getBytes());
        routing.PutValue(closable, ipnsKey, bytes);
    }



    public static Adder getFileAdder(@NonNull Storage storage) {


        BlockStore bs = BlockStore.NewBlockstore(storage);
        Interface exchange = new Exchange(bs);
        BlockService blockservice = BlockService.New(bs, exchange);
        DagService dagService = DagService.createDagService(blockservice);
        Adder fileAdder = Adder.NewAdder(dagService);

        Prefix prefix = Node.PrefixForCidVersion(1);

        prefix.MhType = Multihash.Type.sha2_256.index;
        prefix.MhLength = -1;

        fileAdder.RawLeaves = false;
        fileAdder.CidBuilder = prefix;


        return fileAdder;
    }


    public static void Rm(@NonNull Closeable closeable, @NonNull Storage storage, @NonNull String cid, boolean recursively) throws ClosedException {

        BlockStore bs = BlockStore.NewBlockstore(storage);
        Interface exchange = new Exchange(bs);
        BlockService blockservice = BlockService.New(bs, exchange);
        DagService dags = DagService.createDagService(blockservice);
        io.ipfs.format.Node top = Resolver.ResolveNode(closeable, dags, Path.New(cid));
        Objects.requireNonNull(top);
        List<Cid> cids = new ArrayList<>();
        if (recursively) {
            RefWriter rw = new RefWriter(true, -1);

            rw.EvalRefs(top);
            cids.addAll(rw.getCids());
        }

        cids.add(top.Cid());
        bs.DeleteBlocks(cids);

    }

    public static boolean IsDir(@NonNull Closeable closeable,
                                @NonNull BlockStore blockstore,
                                @NonNull Interface exchange,
                                @NonNull String path) throws ClosedException {


        BlockService blockservice = BlockService.New(blockstore, exchange);
        DagService dagService = DagService.createDagService(blockservice);

        io.ipfs.format.Node node = Resolver.ResolveNode(closeable, dagService, Path.New(path));
        Objects.requireNonNull(node);
        Directory dir = Directory.NewDirectoryFromNode(node);
        return dir != null;
    }

    public static String CreateEmptyDir(@NonNull Storage storage) {

        Adder fileAdder = getFileAdder(storage);

        Node nd = fileAdder.CreateEmptyDir();
        return nd.Cid().String();
    }


    public static String AddLinkToDir(@NonNull Storage storage, @NonNull Closeable closeable,
                                      @NonNull String dir, @NonNull String name, @NonNull String link) throws ClosedException {

        Adder fileAdder = getFileAdder(storage);

        BlockStore bs = BlockStore.NewBlockstore(storage);
        Interface exchange = new Exchange(bs);
        BlockService blockservice = BlockService.New(bs, exchange);
        DagService dagService = DagService.createDagService(blockservice);

        io.ipfs.format.Node dirNode = Resolver.ResolveNode(closeable, dagService, Path.New(dir));
        Objects.requireNonNull(dirNode);
        io.ipfs.format.Node linkNode = Resolver.ResolveNode(closeable, dagService, Path.New(link));
        Objects.requireNonNull(linkNode);
        Node nd = fileAdder.AddLinkToDir(dirNode, name, linkNode);
        return nd.Cid().String();

    }

    public static String RemoveLinkFromDir(@NonNull Storage storage, @NonNull Closeable closeable,
                                           @NonNull String dir, @NonNull String name) throws ClosedException {

        Adder fileAdder = getFileAdder(storage);

        BlockStore bs = BlockStore.NewBlockstore(storage);
        Interface exchange = new Exchange(bs);
        BlockService blockservice = BlockService.New(bs, exchange);
        DagService dagService = DagService.createDagService(blockservice);

        io.ipfs.format.Node dirNode = Resolver.ResolveNode(closeable, dagService, Path.New(dir));
        Objects.requireNonNull(dirNode);
        Node nd = fileAdder.RemoveChild(dirNode, name);
        return nd.Cid().String();

    }

    public static void Ls(@NonNull LinkCloseable closeable, @NonNull BlockStore blockstore,
                          @NonNull Interface exchange,
                          @NonNull String path, boolean resolveChildren) throws ClosedException {

        BlockService blockservice = BlockService.New(blockstore, exchange);
        DagService dagService = DagService.createDagService(blockservice);


        io.ipfs.format.Node node = Resolver.ResolveNode(closeable, dagService, Path.New(path));
        Objects.requireNonNull(node);
        Directory dir = Directory.NewDirectoryFromNode(node);

        if (dir == null) {
            lsFromLinks(closeable, dagService, node.getLinks(), resolveChildren);
        } else {
            lsFromLinksAsync(closeable, dagService, dir, resolveChildren);
        }

    }


    public static String Write(@NonNull Storage storage,
                               @NonNull WriterStream writerStream) {

        Adder fileAdder = getFileAdder(storage);
        Node node = fileAdder.AddReader(writerStream);
        return node.Cid().String();
    }

    private static void lsFromLinksAsync(@NonNull LinkCloseable closeable,
                                         @NonNull DagService dagService,
                                         @NonNull Directory dir,
                                         boolean resolveChildren) throws ClosedException {

        List<Link> links = dir.GetNode().getLinks();
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

        if (cid.Type() == Cid.Raw) {
            closeable.info(io.ipfs.utils.Link.create(name, hash, size, io.ipfs.utils.Link.File));
        } else if (cid.Type() == Cid.DagProtobuf) {
            if (!resolveChildren) {
                closeable.info(io.ipfs.utils.Link.create(name, hash, size, io.ipfs.utils.Link.NotKnown));
            } else {

                Node linkNode = link.GetNode(closeable, dagService);
                if (linkNode instanceof ProtoNode) {
                    ProtoNode pn = (ProtoNode) linkNode;
                    FSNode d = FSNode.FSNodeFromBytes(pn.getData());
                    int type;
                    switch (d.Type()) {
                        case File:
                            type = io.ipfs.utils.Link.Raw;
                            break;
                        case Raw:
                            type = io.ipfs.utils.Link.File;
                            break;
                        case Symlink:
                            type = io.ipfs.utils.Link.Symlink;
                            break;
                        default:
                            type = io.ipfs.utils.Link.Dir;
                    }
                    size = d.FileSize();
                    closeable.info(io.ipfs.utils.Link.create(name, hash, size, type));

                }
            }
        }
    }
}
