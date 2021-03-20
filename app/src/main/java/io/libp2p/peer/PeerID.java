package io.libp2p.peer;

import androidx.annotation.NonNull;

import java.util.Objects;

public class PeerID implements Comparable<PeerID> {
    private final String id;

    public PeerID(@NonNull String id) {
        this.id = id;
    }

    public String String() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerID id1 = (PeerID) o;
        return Objects.equals(id, id1.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public int compareTo(PeerID o) {
        return id.compareTo(o.id);
    }
}
