package io.crypto;

import org.jetbrains.annotations.NotNull;

import crypto.pb.Crypto;

public abstract class PubKey implements Key {
    @NotNull
    private final Crypto.KeyType keyType;

    public PubKey(@NotNull Crypto.KeyType keyType) {
        super();
        this.keyType = keyType;
    }

    public abstract boolean verify(@NotNull byte[] var1, @NotNull byte[] var2);

    @NotNull
    public byte[] bytes() {
        return Key.marshalPublicKey(this);
    }

    @NotNull
    public Crypto.KeyType getKeyType() {
        return this.keyType;
    }
}

