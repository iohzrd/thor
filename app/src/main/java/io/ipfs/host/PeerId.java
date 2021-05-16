package io.ipfs.host;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import io.core.BufferExtKt;
import io.core.ByteArrayExtKt;
import io.crypto.KeyKt;
import io.crypto.PubKey;

import io.ipfs.multibase.Base58;
import io.ipfs.multiformats.Multihash;
import kotlin.jvm.internal.Intrinsics;
import kotlin.random.Random;

public class PeerId {
    @NotNull
    private final byte[] bytes;

    public PeerId(@NotNull byte[] bytes) {
        Intrinsics.checkNotNullParameter(bytes, "bytes");

        this.bytes = bytes;
        if (this.bytes.length < 32 || this.bytes.length > 50) {
            throw new IllegalArgumentException("Invalid peerId length: " + this.bytes.length);
        }
    }

    @NotNull
    public static PeerId fromBase58(@NotNull String str) {
        Intrinsics.checkNotNullParameter(str, "str");
        return new PeerId(Base58.decode(str));
    }

    @NotNull
    public static PeerId fromHex(@NotNull String str) {
        Intrinsics.checkNotNullParameter(str, "str");
        return new PeerId(ByteArrayExtKt.fromHex(str));
    }

    @NotNull
    public static PeerId fromPubKey(@NotNull PubKey pubKey) {
        Intrinsics.checkNotNullParameter(pubKey, "pubKey");
        byte[] pubKeyBytes = KeyKt.marshalPublicKey(pubKey);
        Multihash.Descriptor descriptor = pubKeyBytes.length <= 42 ? new Multihash.Descriptor(Multihash.Digest.Identity, null) : new Multihash.Descriptor(Multihash.Digest.SHA2, 256);

        Multihash mh = io.ipfs.multiformats.Multihash.digest(descriptor, BufferExtKt.toByteBuf(pubKeyBytes), null);
        return new PeerId(BufferExtKt.toByteArray(mh.getBytes()));
    }

    @NotNull
    public static PeerId random() {
        return new PeerId(Random.Default.nextBytes(32));
    }

    @NotNull
    public final String toBase58() {
        return Base58.encode(this.bytes);
    }

    @NotNull
    public final String toHex() {
        return ByteArrayExtKt.toHex(this.bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerId peer = (PeerId) o;
        return Arrays.equals(bytes, peer.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @NotNull
    public String toString() {
        return this.toBase58();
    }

    @NotNull
    public final byte[] getBytes() {
        return this.bytes;
    }
}
