package threads.thor.ipfs;

import androidx.annotation.NonNull;

import java.util.Objects;

public class Link {

    @NonNull
    private final String hash;
    @NonNull
    private final String name;


    private Link(@NonNull String hash,
                 @NonNull String name) {
        this.hash = hash;
        this.name = name;
    }

    public static Link create(String name, String hash) {
        Objects.requireNonNull(hash);
        Objects.requireNonNull(name);

        return new Link(hash, name);
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
        return hash.equals(linkInfo.hash) &&
                name.equals(linkInfo.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash, name);
    }


    @NonNull
    public String getName() {
        return name;
    }



    @NonNull
    @Override
    public String toString() {
        return "Link{" +
                "hash='" + hash + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}

