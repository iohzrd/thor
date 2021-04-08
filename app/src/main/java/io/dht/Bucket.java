package io.dht;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.Objects;

import io.libp2p.core.PeerId;

public class Bucket extends LinkedList<Bucket.PeerInfo> {


    @Nullable
    public PeerInfo getPeer(PeerId p) {
        for (PeerInfo peerInfo : this) {
            if (Objects.equals(peerInfo.peerId, p)) {
                return peerInfo;
            }
        }
        return null;
    }

    public static class PeerInfo {
        @NonNull
        private final PeerId peerId;
        // Id of the peer in the DHT XOR keyspace
        private final ID id;
        // LastUsefulAt is the time instant at which the peer was last "useful" to us.
        // Please see the DHT docs for the definition of usefulness.
        long LastUsefulAt;

        // LastSuccessfulOutboundQueryAt is the time instant at which we last got a
        // successful query response from the peer.
        long LastSuccessfulOutboundQueryAt;

        // AddedAt is the time this peer was added to the routing table.
        long AddedAt;
        // if a bucket is full, this peer can be replaced to make space for a new peer.
        boolean replaceable;

        public PeerInfo(@NonNull PeerId peerId, @NonNull ID id) {
            this.peerId = peerId;
            this.id = id;
        }

        @NonNull
        public PeerId getPeerId() {
            return peerId;
        }

        public ID getID() {
            return id;
        }
    }
}
