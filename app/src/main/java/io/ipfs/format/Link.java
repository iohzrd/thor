package io.ipfs.format;

import androidx.annotation.NonNull;

import java.util.Objects;

import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;

public class Link {

    @NonNull
    private final Cid cid;
    @NonNull
    private final String name;
    private final long size;


    Link(@NonNull Cid cid, @NonNull String name, long size) {
        this.cid = cid;
        this.name = name;
        this.size = size;
    }

    public static Link create(@NonNull byte[] hash, @NonNull String name, long size) {
        Objects.requireNonNull(hash);
        Objects.requireNonNull(name);
        Cid cid = new Cid(hash);
        return new Link(cid, name, size);
    }

    public static Link createLink(@NonNull Node node, @NonNull String name) {
        long size = node.Size();
        return new Link(node.getCid(), name, size);
    }

    @NonNull
    public Cid getCid() {
        return cid;
    }

    public long getSize() {
        return size;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public String toString() {
        return "Link{" +
                "cid='" + cid + '\'' +
                ", name='" + name + '\'' +
                ", size=" + size +
                '}';
    }

    public Node getNode(@NonNull Closeable ctx, @NonNull NodeGetter nodeGetter) throws ClosedException {
        return nodeGetter.getNode(ctx, getCid(), true);
    }

}
