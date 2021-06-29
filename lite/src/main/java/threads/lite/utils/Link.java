package threads.lite.utils;

import androidx.annotation.NonNull;

import java.util.Objects;

public class Link {
    public static final int Raw = 3;
    public static final int File = 2;
    public static final int Dir = 1;
    public static final int Symlink = 4;
    public static final int NotKnown = 8;

    @NonNull
    private final String hash;
    @NonNull
    private final String name;
    private final long size;
    private final int type;


    private Link(@NonNull String hash, @NonNull String name, long size, int type) {
        this.hash = hash;
        this.name = name;
        this.size = size;
        this.type = type;
    }

    public static Link create(String name, String hash, long size, int type) {
        Objects.requireNonNull(hash);
        Objects.requireNonNull(name);

        return new Link(hash, name, size, type);
    }

    @NonNull
    public String getContent() {
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Link linkInfo = (Link) o;
        return size == linkInfo.size &&
                type == linkInfo.type &&
                hash.equals(linkInfo.hash) &&
                name.equals(linkInfo.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash, name, size, type);
    }

    public long getSize() {
        return size;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public boolean isDirectory() {
        return type == Dir;
    }


    @NonNull
    @Override
    public String toString() {
        return "LinkInfo{" +
                "hash='" + hash + '\'' +
                ", name='" + name + '\'' +
                ", size=" + size +
                ", type=" + type +
                '}';
    }

    public boolean isFile() {
        return type == File;

    }

    public boolean isRaw() {
        return type == Raw;
    }
}

