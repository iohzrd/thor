package io.dht;

import androidx.annotation.NonNull;

import java.security.MessageDigest;

import io.libp2p.core.PeerId;

public class Util {

    // ConvertPeerID creates a DHT ID by hashing a Peer ID (Multihash)
    public static ID ConvertPeerID(@NonNull PeerId id) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new ID(digest.digest(id.getBytes()));
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    // ConvertKey creates a DHT ID by hashing a local key (String)
    public static ID ConvertKey(@NonNull String id) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new ID(digest.digest(id.getBytes()));
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
}
