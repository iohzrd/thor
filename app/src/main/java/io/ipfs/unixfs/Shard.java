package io.ipfs.unixfs;

import androidx.annotation.NonNull;

import org.apache.commons.codec.digest.MurmurHash3;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.ipfs.cid.Builder;
import io.ipfs.cid.Cid;
import io.core.Closeable;
import io.core.ClosedException;
import io.ipfs.format.Link;
import io.ipfs.format.Node;
import io.ipfs.merkledag.DagService;

public class Shard {

    private static final int SHARED_LINK = 1;
    private static final int SHARED_VALUE_LINK = 2;
    private final int tableSize;
    private final DagService dserv;
    public Childer childer;
    public Cid cid;
    public Builder builder;


    // leaf node
    public String key;
    public Link val;
    int tableSizeLg2;
    String prefixPadStr;
    int maxpadlen;


    public Shard(DagService ds, Childer childer, String prefixPadStr, int log2s, int maxpadlen, int size) {
        this.dserv = ds;
        this.childer = childer;
        this.tableSize = size;
        this.maxpadlen = maxpadlen;
        this.tableSizeLg2 = log2s;
        this.prefixPadStr = prefixPadStr;
    }

    public static int log2(int num) {
        return (int) (Math.log(num) / Math.log(2));
    }

    public static Shard makeShard(DagService ds, int size) {
        int lg2s = log2(size);//BitField.logtwo(size);
        if (1 << lg2s != size) {
            throw new RuntimeException("hamt size should be a power of two");
        }
        String maxpadding = String.format(Locale.US, "%X", (size - 1));
        String prefixPadStr = String.format(Locale.US, "%%0%dX", maxpadding.length());
        Childer childer = new Childer(ds, size);
        Shard s = new Shard(ds, childer, prefixPadStr, lg2s, maxpadding.length(), size);
        s.childer.sd = s;
        return s;
    }

    @NonNull
    public Link Find(@NonNull Closeable closeable, @NonNull String name) throws ClosedException {

        HashBits hv = new HashBits(hash(name));
        Shard out = getValue(closeable, hv, name);
        return out.val;
    }

    public Shard getValue(@NonNull Closeable closeable, @NonNull HashBits hv, @NonNull String nkey) throws ClosedException {


        int childIndex = hv.Next(tableSizeLg2);

        if (childer.has(childIndex)) {
            Shard child = childer.get(closeable, childer.sliceIndex(childIndex));


            if (child.isValueNode()) {
                if (Objects.equals(child.key, nkey)) {
                    return child;
                }
            } else {
                return child.getValue(closeable, hv, nkey);
            }
        }
        throw new RuntimeException();
    }

    private boolean isValueNode() {
        return (!("".equals(key))) && val != null;
    }

    public byte[] hash(@NonNull String name) {
        return Sum(name.getBytes());
    }

    private Shard makeShardValue(Link lnk) {
        Link lnk2 = lnk;
        Shard s = makeShard(dserv, tableSize);
        s.key = lnk.getName().substring(0, maxpadlen);
        s.val = lnk2;

        return s;
    }

    private int childLinkType(Link lnk) {
        if (lnk.getName().length() < maxpadlen) {
            throw new RuntimeException("invalid link name " + lnk.getName());
        }
        if (lnk.getName().length() == maxpadlen) {
            return SHARED_LINK;
        }
        return SHARED_VALUE_LINK;
    }

    public byte[] Sum(byte[] b) {
        long[] res = MurmurHash3.hash128x64(b);
        long h1 = res[0];
        long h2 = res[1];
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            outputStream.write(b);
            outputStream.write((int) (h1 >> 56));
            outputStream.write((int) (h1 >> 48));
            outputStream.write((int) (h1 >> 40));
            outputStream.write((int) (h1 >> 32));
            outputStream.write((int) (h1 >> 24));
            outputStream.write((int) (h1 >> 16));
            outputStream.write((int) (h1 >> 8));
            outputStream.write((int) (h1));
            outputStream.write((int) (h2 >> 56));
            outputStream.write((int) (h2 >> 48));
            outputStream.write((int) (h2 >> 40));
            outputStream.write((int) (h2 >> 32));
            outputStream.write((int) (h2 >> 24));
            outputStream.write((int) (h2 >> 16));
            outputStream.write((int) (h2 >> 8));
            outputStream.write((int) (h2));
            return outputStream.toByteArray();
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }

    }

    public static class Childer {

        Shard sd;
        DagService dserv;
        BitField bitfield;

        // Only one of links/children will be non-nil for every child/link.
        List<Link> links = new ArrayList<>();
        List<Shard> children;

        private Childer(@NonNull DagService dagService, int size) {
            this.dserv = dagService;
            this.bitfield = BitField.NewBitfield(size);
        }

        public void makeChilder(byte[] data, List<Link> links) {
            children = new ArrayList<>();
            for (int i = 0; i < links.size(); i++) {
                children.add(null);
            }
            bitfield.SetBytes(data);
            this.links.addAll(links);
        }

        public boolean has(int childIndex) {
            return bitfield.Bit(childIndex);
        }

        // get returns the i'th child of this shard. If it is cached in the
// children array, it will return it from there. Otherwise, it loads the child
// node from disk.
        Shard get(Closeable ctx, int sliceIndex) throws ClosedException {
            check(sliceIndex);

            Shard c = child(sliceIndex);
            if (c != null) {
                return c;
            }

            return loadChild(ctx, sliceIndex);
        }

        Link link(int sliceIndex) {
            return links.get(sliceIndex);
        }


        void set(Shard sd, int i) {
            children.set(i, sd);
            links.set(i, null);
        }

        private Shard loadChild(Closeable ctx, int sliceIndex) throws ClosedException {
            Link lnk = link(sliceIndex);
            int lnkLinkType = sd.childLinkType(lnk);


            Shard c;
            if (lnkLinkType == SHARED_LINK) {
                Node nd = lnk.GetNode(ctx, dserv);
                c = Hamt.NewHamtFromDag(dserv, nd);
            } else {
                c = sd.makeShardValue(lnk);
            }

            set(c, sliceIndex);

            return c;
        }

        private Shard child(int sliceIndex) {
            return children.get(sliceIndex);
        }


        private void check(int sliceIndex) {
            if (sliceIndex >= children.size() || sliceIndex < 0) {
                throw new RuntimeException("invalid index passed to operate children (likely corrupt bitfield)");
            }

            if (children.size() != links.size()) {
                throw new RuntimeException("inconsistent lengths between children array and Links array");
            }

        }


        public int sliceIndex(int childIndex) {
            return bitfield.OnesBefore(childIndex);
        }
    }

    public static class HashBits {
        int consumed;
        byte[] b;

        public HashBits(@NonNull byte[] bytes) {
            this.b = bytes;
        }

        public int Next(int i) {

            if (consumed + i > (b.length * 8)) {
                throw new RuntimeException("sharded directory too deep");
            }
            return next(i);
        }

        byte mkmask(int n) {
            return (byte) ((1 << (byte) n) - 1);
        }

        int next(int i) {
            int curbi = consumed / 8;
            int leftb = 8 - (consumed % 8);

            byte curb = b[curbi];
            if (i == leftb) {
                int out = mkmask(i) & curb;
                consumed += i;
                return out;
            } else if (i < leftb) {
                int a = curb & mkmask(leftb); // mask out the high bits we don't want
                int b = a & ~mkmask(leftb - i); // mask out the low bits we don't want
                int c = b >> (leftb - i);   // shift whats left down
                consumed += i;
                return c;
            } else {
                int out = mkmask(leftb) & curb;
                out <<= (i - leftb);
                consumed += leftb;
                out += next(i - leftb);
                return out;
            }
        }
    }

}
